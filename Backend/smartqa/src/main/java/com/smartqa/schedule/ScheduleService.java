package com.smartqa.schedule;

import com.smartqa.audit.AuditService;
import com.smartqa.security.ProjectAccessGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScheduleService {

    private final AuditService auditService;
    private final boolean enabled;
    private final Map<UUID, ScheduledRun> runs = new ConcurrentHashMap<>();

    public ScheduleService(
            AuditService auditService,
            @Value("${smartqa.schedule.enabled:false}") boolean enabled) {
        this.auditService = auditService;
        this.enabled = enabled;
    }

    public ScheduledRun create(UUID projectId, UUID testCaseId, String cron) {
        ScheduledRun run = new ScheduledRun(
                UUID.randomUUID(), projectId, testCaseId, cron, true, null, "PENDING", Instant.now());
        runs.put(run.id(), run);
        return run;
    }

    public List<ScheduledRun> list(UUID projectId) {
        List<ScheduledRun> out = new ArrayList<>();
        for (ScheduledRun run : runs.values()) {
            if (projectId.equals(run.projectId())) {
                out.add(run);
            }
        }
        return out;
    }

    public ScheduledRun disable(UUID projectId, UUID scheduleId) {
        ScheduledRun existing = runs.get(scheduleId);
        if (existing == null) {
            throw new IllegalArgumentException("Schedule not found");
        }
        ProjectAccessGuard.assertSameProject(projectId, existing.projectId(), "schedule");
        ScheduledRun updated = existing.disabled();
        runs.put(scheduleId, updated);
        return updated;
    }

    @Scheduled(fixedDelay = 60_000L)
    public void pollDueRuns() {
        if (!enabled) {
            return;
        }
        for (ScheduledRun run : runs.values()) {
            if (run.enabled()) {
                auditService.record(run.projectId(), run.testCaseId(), "scheduler", "SCHEDULE_DUE", run.id().toString())
                        .onErrorResume(error -> Mono.empty())
                        .subscribe();
            }
        }
    }
}
