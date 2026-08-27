package com.smartqa.testcase;

import com.smartqa.common.error.ResourceNotFoundException;
import com.smartqa.project.Project;
import com.smartqa.project.ProjectRepository;
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
class TestCaseServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TestCaseRepository testCaseRepository;
    @Mock
    private TestScenarioRepository scenarioRepository;
    @Mock
    private TestStepRepository stepRepository;

    @InjectMocks
    private TestCaseService testCaseService;

    @Test
    void createPersistsDefaultScenarioAndSteps() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);

        UUID testCaseId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        when(projectRepository.findById(projectId)).thenReturn(Mono.just(project));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(invocation -> {
            TestCase saved = invocation.getArgument(0);
            saved.setId(testCaseId);
            return Mono.just(saved);
        });
        when(scenarioRepository.findByTestCaseIdOrderByScenarioOrderAsc(testCaseId))
                .thenReturn(Flux.empty())
                .thenReturn(Flux.just(scenario(scenarioId, testCaseId)));
        when(scenarioRepository.deleteByTestCaseId(testCaseId)).thenReturn(Mono.empty());
        when(scenarioRepository.save(any(TestScenario.class))).thenAnswer(invocation -> {
            TestScenario saved = invocation.getArgument(0);
            saved.setId(scenarioId);
            return Mono.just(saved);
        });
        when(stepRepository.save(any(TestStep.class))).thenAnswer(invocation -> {
            TestStep saved = invocation.getArgument(0);
            saved.setId(stepId);
            return Mono.just(saved);
        });
        when(stepRepository.findByScenarioIdOrderByStepOrderAsc(scenarioId))
                .thenReturn(Flux.just(step(stepId, scenarioId, 1, "Open https://example.com")));

        StepVerifier.create(testCaseService.create(projectId, new TestCaseRequest(
                        "Example", null, "Open https://example.com")))
                .expectNextMatches(response ->
                        response.id().equals(testCaseId)
                                && "DRAFT".equals(response.status())
                                && response.scenarios().size() == 1
                                && response.scenarios().getFirst().steps().size() == 1
                                && "Open https://example.com".equals(response.scenarios().getFirst().steps().getFirst().text()))
                .verifyComplete();
    }

    @Test
    void getMissingTestCaseErrors() {
        UUID id = UUID.randomUUID();
        when(testCaseRepository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(testCaseService.get(id))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    void createMissingProjectErrors() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Mono.empty());

        StepVerifier.create(testCaseService.create(projectId, new TestCaseRequest(
                        "Example", null, "Open https://example.com")))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    private static TestScenario scenario(UUID id, UUID testCaseId) {
        TestScenario scenario = new TestScenario();
        scenario.setId(id);
        scenario.setTestCaseId(testCaseId);
        scenario.setScenarioName("Main");
        scenario.setScenarioOrder(1);
        return scenario;
    }

    private static TestStep step(UUID id, UUID scenarioId, int order, String text) {
        TestStep step = new TestStep();
        step.setId(id);
        step.setScenarioId(scenarioId);
        step.setStepOrder(order);
        step.setStepText(text);
        return step;
    }
}
