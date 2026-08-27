package com.smartqa.project;

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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {ProjectController.class, GlobalExceptionHandler.class})
class ProjectControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ProjectService projectService;

    @Test
    void listProjects() {
        ProjectResponse response = sample(UUID.randomUUID());
        when(projectService.list()).thenReturn(Flux.just(response));

        webTestClient.get()
                .uri("/api/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].name").isEqualTo("Demo");
    }

    @Test
    void createProject() {
        ProjectResponse response = sample(UUID.randomUUID());
        when(projectService.create(any())).thenReturn(Mono.just(response));

        webTestClient.post()
                .uri("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Demo","applicationUrl":"https://example.com","environment":"local"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.name").isEqualTo("Demo");
    }

    @Test
    void missingProjectReturns404() {
        UUID id = UUID.randomUUID();
        when(projectService.get(eq(id))).thenReturn(Mono.error(new ResourceNotFoundException("missing")));

        webTestClient.get()
                .uri("/api/projects/" + id)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.errorCode").isEqualTo("RESOURCE_NOT_FOUND");
    }

    private static ProjectResponse sample(UUID id) {
        LocalDateTime now = LocalDateTime.now();
        return new ProjectResponse(id, "Demo", "desc", "https://example.com", "local", 0, now, now);
    }
}
