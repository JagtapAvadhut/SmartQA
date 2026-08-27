package com.smartqa.testcase;

import com.smartqa.common.error.GlobalExceptionHandler;
import com.smartqa.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {TestCaseController.class, GlobalExceptionHandler.class})
class TestCaseControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TestCaseService testCaseService;

    @Test
    void listTestCases() {
        UUID projectId = UUID.randomUUID();
        when(testCaseService.listByProject(projectId)).thenReturn(Flux.just(sample(UUID.randomUUID(), projectId)));

        webTestClient.get()
                .uri("/api/projects/" + projectId + "/test-cases")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].name").isEqualTo("Example");
    }

    @Test
    void createTestCase() {
        UUID projectId = UUID.randomUUID();
        when(testCaseService.create(eq(projectId), any())).thenReturn(Mono.just(sample(UUID.randomUUID(), projectId)));

        webTestClient.post()
                .uri("/api/projects/" + projectId + "/test-cases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Example","naturalLanguage":"Open https://example.com"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.name").isEqualTo("Example")
                .jsonPath("$.data.status").isEqualTo("DRAFT");
    }

    @Test
    void missingTestCaseReturns404() {
        UUID id = UUID.randomUUID();
        when(testCaseService.get(id)).thenReturn(Mono.error(new ResourceNotFoundException("missing")));

        webTestClient.get()
                .uri("/api/test-cases/" + id)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.errorCode").isEqualTo("RESOURCE_NOT_FOUND");
    }

    private static TestCaseResponse sample(UUID id, UUID projectId) {
        LocalDateTime now = LocalDateTime.now();
        return new TestCaseResponse(
                id,
                projectId,
                "Example",
                null,
                "DRAFT",
                "Open https://example.com",
                null,
                null,
                null,
                List.of(new TestCaseResponse.ScenarioResponse(
                        UUID.randomUUID(),
                        "Main",
                        1,
                        List.of(new TestCaseResponse.StepResponse(UUID.randomUUID(), 1, "Open https://example.com"))
                )),
                now,
                now
        );
    }
}
