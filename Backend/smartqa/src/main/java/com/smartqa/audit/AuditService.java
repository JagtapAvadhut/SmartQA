package com.smartqa.audit;

import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final DatabaseClient databaseClient;

    public AuditService(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<Void> record(UUID projectId, UUID testCaseId, String actor, String action, String detail) {
        TraceLogger.info("AUDIT", action, "Audit event", TraceMeta.of(
                "projectId", projectId == null ? "" : projectId.toString(),
                "testCaseId", testCaseId == null ? "" : testCaseId.toString(),
                "actor", actor == null ? "" : actor
        ));
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO audit_events (id, project_id, test_case_id, actor, action, detail, created_at)
                        VALUES (:id, :projectId, :testCaseId, :actor, :action, :detail, now())
                        """)
                .bind("id", UUID.randomUUID());
        spec = bindUuid(spec, "projectId", projectId);
        spec = bindUuid(spec, "testCaseId", testCaseId);
        return spec
                .bind("actor", actor == null ? "system" : actor)
                .bind("action", action)
                .bind("detail", detail == null ? "" : detail)
                .then()
                .onErrorResume(error -> {
                    TraceLogger.warn("AUDIT", "AUDIT_PERSIST_FAILED", error.getMessage(), Map.of());
                    return Mono.empty();
                });
    }

    private static DatabaseClient.GenericExecuteSpec bindUuid(
            DatabaseClient.GenericExecuteSpec spec, String name, UUID value) {
        if (value == null) {
            return spec.bindNull(name, UUID.class);
        }
        return spec.bind(name, value);
    }
}
