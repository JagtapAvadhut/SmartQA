package com.smartqa.schedule;

import java.time.Instant;
import java.util.UUID;

public record ScheduledRun(
        UUID id,
        UUID projectId,
        UUID testCaseId,
        String cron,
        boolean enabled,
        Instant lastRunAt,
        String lastStatus,
        Instant createdAt
) {
    public ScheduledRun disabled() {
        return new ScheduledRun(id, projectId, testCaseId, cron, false, lastRunAt, lastStatus, createdAt);
    }
}
