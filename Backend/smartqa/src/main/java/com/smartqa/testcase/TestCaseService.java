package com.smartqa.testcase;

import com.smartqa.common.NaturalLanguage;
import com.smartqa.common.error.ResourceNotFoundException;
import com.smartqa.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TestCaseService {

    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestScenarioRepository scenarioRepository;
    private final TestStepRepository stepRepository;

    public TestCaseService(
            ProjectRepository projectRepository,
            TestCaseRepository testCaseRepository,
            TestScenarioRepository scenarioRepository,
            TestStepRepository stepRepository) {
        this.projectRepository = projectRepository;
        this.testCaseRepository = testCaseRepository;
        this.scenarioRepository = scenarioRepository;
        this.stepRepository = stepRepository;
    }

    public Flux<TestCaseResponse> listByProject(UUID projectId) {
        return testCaseRepository.findByProjectIdOrderByUpdatedAtDesc(projectId)
                .flatMap(this::toResponse);
    }

    public Mono<TestCaseResponse> get(UUID id) {
        return testCaseRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Test case not found: " + id)))
                .flatMap(this::toResponse);
    }

    @Transactional
    public Mono<TestCaseResponse> create(UUID projectId, TestCaseRequest request) {
        return projectRepository.findById(projectId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Project not found: " + projectId)))
                .flatMap(project -> {
                    LocalDateTime now = LocalDateTime.now();
                    TestCase testCase = new TestCase();
                    testCase.setProjectId(project.getId());
                    testCase.setStatus(TestCaseStatus.DRAFT);
                    apply(testCase, request);
                    testCase.setCreatedAt(now);
                    testCase.setUpdatedAt(now);
                    return testCaseRepository.save(testCase)
                            .flatMap(saved -> replaceScenarios(saved, parseSteps(request.naturalLanguage())));
                });
    }

    @Transactional
    public Mono<TestCaseResponse> update(UUID id, TestCaseRequest request) {
        return testCaseRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Test case not found: " + id)))
                .flatMap(existing -> {
                    String nextLanguage = NaturalLanguage.normalize(request.naturalLanguage());
                    boolean instructionsChanged = existing.getNaturalLanguage() == null
                            || !existing.getNaturalLanguage().equals(nextLanguage);
                    apply(existing, request);
                    existing.setUpdatedAt(LocalDateTime.now());
                    if (instructionsChanged) {
                        existing.setStatus(TestCaseStatus.DRAFT);
                    }
                    return testCaseRepository.save(existing)
                            .flatMap(saved -> replaceScenarios(saved, parseSteps(request.naturalLanguage())));
                });
    }

    public Mono<Void> delete(UUID id) {
        return requireEntity(id).flatMap(testCaseRepository::delete);
    }

    public Mono<TestCase> requireEntity(UUID id) {
        return testCaseRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Test case not found: " + id)));
    }

    public Mono<TestCase> saveEntity(TestCase testCase) {
        testCase.setUpdatedAt(LocalDateTime.now());
        return testCaseRepository.save(testCase);
    }

    public static List<String> parseSteps(String naturalLanguage) {
        List<String> steps = new ArrayList<>();
        if (naturalLanguage == null) {
            return steps;
        }
        for (String line : naturalLanguage.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                steps.add(trimmed.replaceFirst("^\\d+\\.\\s*", ""));
            }
        }
        return steps;
    }

    private Mono<TestCaseResponse> replaceScenarios(TestCase testCase, List<String> steps) {
        return scenarioRepository.findByTestCaseIdOrderByScenarioOrderAsc(testCase.getId())
                .flatMap(scenario -> stepRepository.deleteByScenarioId(scenario.getId()).thenReturn(scenario))
                .collectList()
                .flatMap(ignored -> scenarioRepository.deleteByTestCaseId(testCase.getId()))
                .then(Mono.defer(() -> {
                    TestScenario scenario = new TestScenario();
                    scenario.setTestCaseId(testCase.getId());
                    scenario.setScenarioName("Main");
                    scenario.setScenarioOrder(1);
                    return scenarioRepository.save(scenario);
                }))
                .flatMap(scenario -> saveSteps(scenario, steps).thenReturn(testCase))
                .flatMap(this::toResponse);
    }

    private Mono<Void> saveSteps(TestScenario scenario, List<String> steps) {
        if (steps.isEmpty()) {
            return Mono.empty();
        }
        List<Mono<TestStep>> saves = new ArrayList<>();
        int order = 1;
        for (String text : steps) {
            TestStep step = new TestStep();
            step.setScenarioId(scenario.getId());
            step.setStepOrder(order++);
            step.setStepText(text);
            saves.add(stepRepository.save(step));
        }
        return Flux.concat(saves).then();
    }

    private Mono<TestCaseResponse> toResponse(TestCase testCase) {
        return scenarioRepository.findByTestCaseIdOrderByScenarioOrderAsc(testCase.getId())
                .flatMap(scenario -> stepRepository.findByScenarioIdOrderByStepOrderAsc(scenario.getId())
                        .map(step -> new TestCaseResponse.StepResponse(step.getId(), step.getStepOrder(), step.getStepText()))
                        .collectList()
                        .map(steps -> new TestCaseResponse.ScenarioResponse(
                                scenario.getId(), scenario.getScenarioName(), scenario.getScenarioOrder(), steps)))
                .collectList()
                .map(scenarios -> new TestCaseResponse(
                        testCase.getId(),
                        testCase.getProjectId(),
                        testCase.getName(),
                        testCase.getDescription(),
                        testCase.getStatus(),
                        testCase.getNaturalLanguage(),
                        testCase.getGeneratedCode(),
                        testCase.getLocatorMemory(),
                        testCase.getIntentContract(),
                        scenarios,
                        testCase.getCreatedAt(),
                        testCase.getUpdatedAt()
                ));
    }

    private void apply(TestCase testCase, TestCaseRequest request) {
        testCase.setName(request.name().trim());
        testCase.setDescription(blankToNull(request.description()));
        testCase.setNaturalLanguage(NaturalLanguage.normalize(request.naturalLanguage()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
