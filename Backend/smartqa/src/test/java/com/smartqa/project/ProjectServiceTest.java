package com.smartqa.project;

import com.smartqa.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private com.smartqa.testcase.TestCaseRepository testCaseRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createPersistsProject() {
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return Mono.just(saved);
        });
        when(testCaseRepository.countByProjectId(any())).thenReturn(Mono.just(0L));

        StepVerifier.create(projectService.create(new ProjectRequest(
                        "Demo", "desc", "https://example.com", "local")))
                .expectNextMatches(response ->
                        response.name().equals("Demo")
                                && response.applicationUrl().equals("https://example.com")
                                && response.id() != null)
                .verifyComplete();
    }

    @Test
    void getMissingProjectErrors() {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(projectService.get(id))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    void listReturnsProjects() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName("A");
        project.setApplicationUrl("https://example.com");
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        when(projectRepository.findAll()).thenReturn(Flux.just(project));
        when(testCaseRepository.countByProjectId(project.getId())).thenReturn(Mono.just(2L));

        StepVerifier.create(projectService.list())
                .expectNextMatches(item -> item.name().equals("A"))
                .verifyComplete();
    }
}
