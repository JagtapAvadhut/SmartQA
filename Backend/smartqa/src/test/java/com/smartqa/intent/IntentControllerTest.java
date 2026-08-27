package com.smartqa.intent;

import com.smartqa.common.error.GlobalExceptionHandler;
import com.smartqa.testcase.TestCaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {IntentController.class, GlobalExceptionHandler.class})
class IntentControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private IntentService intentService;

    @Test
    void understandReturnsTestCase() {
        UUID id = UUID.randomUUID();
        when(intentService.understand(id)).thenReturn(Mono.just(sample(id)));

        webTestClient.post()
                .uri("/api/test-cases/" + id + "/understand")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.status").isEqualTo("DRAFT");
    }

    private static TestCaseResponse sample(UUID id) {
        LocalDateTime now = LocalDateTime.now();
        return new TestCaseResponse(
                id, UUID.randomUUID(), "Example", null, "DRAFT", "Open example",
                null, null, "{\"status\":\"READY\"}", List.of(), now, now);
    }
}
