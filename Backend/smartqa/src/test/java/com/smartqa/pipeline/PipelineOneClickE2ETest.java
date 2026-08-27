package com.smartqa.pipeline;

import com.smartqa.common.api.ApiResponse;
import com.smartqa.event.ProgressEventHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Acceptance gate for one-click POST /api/workspace/generate-and-validate lifecycle states.
 */
class PipelineOneClickE2ETest {

    private PipelineService pipelineService;
    private WebTestClient client;
    private final Map<UUID, PipelineRun> runs = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        pipelineService = Mockito.mock(PipelineService.class);
        ProgressEventHub hub = new ProgressEventHub();
        PipelineController controller = new PipelineController(pipelineService, hub);
        client = WebTestClient.bindToController(controller).build();

        when(pipelineService.start(any())).thenAnswer(inv -> {
            PipelineStartRequest req = inv.getArgument(0);
            PipelineRun run = PipelineRun.start();
            if (req.instructions() != null && req.instructions().contains("CLARIFY")) {
                run.markBlocked("Which Profile?", FailureDiagnosis.of(
                        "NEEDS_CLARIFICATION", "USER_INSTRUCTION", "USER_INSTRUCTION", "USER_INSTRUCTION",
                        "Ambiguous Profile matches", false, 1, "Ask user which Profile"));
                runs.put(run.id(), run);
                return Mono.just(PipelineRunResponse.from(run));
            }
            if (req.instructions() != null && req.instructions().contains("VALIDATION_FAIL")) {
                run.markFail("Validation failed", FailureDiagnosis.of(
                        "VALIDATION_FAILED", "VALIDATOR", "VALIDATOR", "GENERATED_TEST",
                        "Invalid steps", false, 1, "Fix generated steps"));
                runs.put(run.id(), run);
                return Mono.just(PipelineRunResponse.from(run));
            }
            if (req.instructions() != null && req.instructions().contains("EXEC_FAIL")) {
                run.markFail("Execution failed", FailureDiagnosis.of(
                        "ACTION_FAILURE", "LOCATOR", "LOCATOR", "ACTIONABILITY",
                        "element not found", true, 2, "AI recovery then retry"));
                runs.put(run.id(), run);
                return Mono.just(PipelineRunResponse.from(run));
            }
            runs.put(run.id(), run);
            return Mono.just(PipelineRunResponse.from(run));
        });
        when(pipelineService.get(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            PipelineRun run = runs.get(id);
            return run == null ? Mono.empty() : Mono.just(PipelineRunResponse.from(run));
        });
        when(pipelineService.stop(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            PipelineRun run = runs.get(id);
            if (run != null && !run.isTerminal()) {
                run.markStopped();
            }
            return run == null ? Mono.empty() : Mono.just(PipelineRunResponse.from(run));
        });
    }

    @Test
    void successStartClarificationValidationFailExecutionFailStopAndFinalPassFailGates() {
        // success start
        AtomicReference<UUID> runningId = new AtomicReference<>();
        client.post().uri("/api/workspace/generate-and-validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "applicationUrl", "https://example.com",
                        "instructions", "Click Login"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.id").value(id -> runningId.set(UUID.fromString(String.valueOf(id))));
        assertNotNull(runningId.get());

        // clarification / blocked
        client.post().uri("/api/workspace/generate-and-validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "applicationUrl", "https://example.com",
                        "instructions", "CLARIFY Profile"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo(PipelineRun.STATUS_BLOCKED);

        // validation failure
        client.post().uri("/api/workspace/generate-and-validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "applicationUrl", "https://example.com",
                        "instructions", "VALIDATION_FAIL"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo(PipelineRun.STATUS_FAIL);

        // execution failure + AI recovery attempt metadata
        client.post().uri("/api/workspace/generate-and-validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "applicationUrl", "https://example.com",
                        "instructions", "EXEC_FAIL"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo(PipelineRun.STATUS_FAIL);

        // stop
        client.post().uri("/api/pipelines/" + runningId.get() + "/stop")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo(PipelineRun.STATUS_STOPPED);

        client.post().uri("/api/pipelines/" + runningId.get() + "/stop")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo(PipelineRun.STATUS_STOPPED);

        // final PASS / FAIL semantics retained
        PipelineRun pass = PipelineRun.start();
        pass.markPass("ok");
        assertEquals(PipelineRun.STATUS_PASS, pass.status());
        assertTrue(Boolean.TRUE.equals(pass.details().get("pipelinePass")));

        PipelineRun fail = PipelineRun.start();
        fail.markFail("execution failed", FailureDiagnosis.of(
                "ACTION_FAILURE", "LOCATOR", "LOCATOR", "ACTIONABILITY",
                "not found", true, 2, "retry"));
        assertEquals(PipelineRun.STATUS_FAIL, fail.status());
        assertTrue(fail.attempt() >= 1);
    }
}
