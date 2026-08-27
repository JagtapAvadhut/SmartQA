package com.smartqa.project;

import com.smartqa.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public Mono<ApiResponse<List<ProjectResponse>>> list() {
        return projectService.list().collectList()
                .map(projects -> ApiResponse.ok("Projects fetched", projects));
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<ProjectResponse>> get(@PathVariable UUID id) {
        return projectService.get(id)
                .map(project -> ApiResponse.ok("Project fetched", project));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<ProjectResponse>> create(@Valid @RequestBody ProjectRequest request) {
        return projectService.create(request)
                .map(project -> ApiResponse.ok("Project created", project));
    }

    @PutMapping("/{id}")
    public Mono<ApiResponse<ProjectResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request)
                .map(project -> ApiResponse.ok("Project updated", project));
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable UUID id) {
        return projectService.delete(id)
                .thenReturn(ApiResponse.ok("Project deleted", null));
    }
}
