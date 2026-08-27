package com.smartqa.project;

import com.smartqa.common.error.ResourceNotFoundException;
import com.smartqa.testcase.TestCaseRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;

    public ProjectService(ProjectRepository projectRepository, TestCaseRepository testCaseRepository) {
        this.projectRepository = projectRepository;
        this.testCaseRepository = testCaseRepository;
    }

    public Flux<ProjectResponse> list() {
        return projectRepository.findAll().flatMap(this::toResponse);
    }

    public Mono<ProjectResponse> get(UUID id) {
        return projectRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Project not found: " + id)))
                .flatMap(this::toResponse);
    }

    public Mono<ProjectResponse> create(ProjectRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Project project = new Project();
        apply(project, request);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        return projectRepository.save(project).flatMap(this::toResponse);
    }

    public Mono<ProjectResponse> update(UUID id, ProjectRequest request) {
        return projectRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Project not found: " + id)))
                .flatMap(existing -> {
                    apply(existing, request);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return projectRepository.save(existing);
                })
                .flatMap(this::toResponse);
    }

    public Mono<Void> delete(UUID id) {
        return projectRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Project not found: " + id)))
                .flatMap(projectRepository::delete);
    }

    private Mono<ProjectResponse> toResponse(Project project) {
        return testCaseRepository.countByProjectId(project.getId())
                .defaultIfEmpty(0L)
                .map(count -> ProjectResponse.from(project, count));
    }

    private void apply(Project project, ProjectRequest request) {
        project.setName(request.name().trim());
        project.setDescription(blankToNull(request.description()));
        project.setApplicationUrl(request.applicationUrl().trim());
        project.setEnvironment(blankToNull(request.environment()));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
