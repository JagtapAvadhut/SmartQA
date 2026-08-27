package com.smartqa.health;

import com.smartqa.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = HealthController.class)
class HealthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ProjectRepository projectRepository;

    @Test
    void healthReturnsUp() {
        when(projectRepository.count()).thenReturn(Mono.just(1L));

        webTestClient.get()
                .uri("/api/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.application").isEqualTo("SmartQA");
    }

    @Test
    void healthReturnsDownWhenDatabaseUnreachable() {
        when(projectRepository.count()).thenReturn(Mono.error(new RuntimeException("db down")));

        webTestClient.get()
                .uri("/api/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DOWN")
                .jsonPath("$.application").isEqualTo("SmartQA");
    }
}
