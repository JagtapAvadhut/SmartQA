package com.smartqa.health;

import com.smartqa.project.ProjectRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Duration DB_PROBE_TIMEOUT = Duration.ofSeconds(2);

    private final ProjectRepository projectRepository;

    public HealthController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<HealthResponse> health() {
        return projectRepository.count()
                .timeout(DB_PROBE_TIMEOUT)
                .map(ignored -> new HealthResponse("UP", "SmartQA"))
                .onErrorReturn(new HealthResponse("DOWN", "SmartQA"));
    }

    @GetMapping(path = "/generation", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GenerationHealthResponse> generationHealth() {
        return Mono.just(new GenerationHealthResponse(
                "UP", "SmartQA", true, "Chromium", "Playwright Java"));
    }
}
