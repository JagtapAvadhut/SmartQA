package com.smartqa.execution.screenshot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ScreenshotControllerTest {

    @Autowired
    private WebTestClient client;

    @Test
    void listScreenshots_returnsEmptyList_forUnknownRun() {
        UUID runId = UUID.randomUUID();
        client.get()
                .uri("/api/execution-runs/{runId}/screenshots", runId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(0);
    }

    @Test
    void getScreenshot_returns404_forUnknownId() {
        client.get()
                .uri("/api/screenshots/{id}", "nonexistent")
                .exchange()
                .expectStatus().isNotFound();
    }
}
