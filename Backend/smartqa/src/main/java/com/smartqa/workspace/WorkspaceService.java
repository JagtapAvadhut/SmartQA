package com.smartqa.workspace;

import com.smartqa.common.NaturalLanguage;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.intent.IntentContract;
import com.smartqa.intent.IntentService;
import com.smartqa.intent.StructuredIntentFactory;
import com.smartqa.project.ProjectRepository;
import com.smartqa.project.ProjectRequest;
import com.smartqa.project.ProjectResponse;
import com.smartqa.project.ProjectService;
import com.smartqa.testcase.TestCaseRequest;
import com.smartqa.testcase.TestCaseResponse;
import com.smartqa.testcase.TestCaseService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

@Service
public class WorkspaceService {

    static final String WORKSPACE_NAME = "SmartQA Workspace";

    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final TestCaseService testCaseService;
    private final IntentService intentService;

    public WorkspaceService(
            ProjectService projectService,
            ProjectRepository projectRepository,
            TestCaseService testCaseService,
            IntentService intentService) {
        this.projectService = projectService;
        this.projectRepository = projectRepository;
        this.testCaseService = testCaseService;
        this.intentService = intentService;
    }

    public Mono<WorkspaceAnalyzeResponse> analyze(WorkspaceAnalyzeRequest request) {
        String url = request.applicationUrl() == null ? "" : request.applicationUrl().trim();
        boolean hasStructured = request.structuredSteps() != null && !request.structuredSteps().isEmpty();
        String instructions = hasStructured
                ? StructuredIntentFactory.toNaturalLanguage(request.structuredSteps().stream()
                .map(step -> new StructuredIntentFactory.StructuredStepInput(
                        step.id(), step.action(), step.target(), step.value(),
                        step.assertion(), step.location(), step.filter()))
                .toList())
                : NaturalLanguage.normalize(request.instructions());
        if (!isHttpUrl(url)) {
            return Mono.error(new SmartQaException(ErrorCode.VALIDATION_FAILED, "Please enter a valid application URL."));
        }
        if (instructions.isBlank() && !hasStructured) {
            return Mono.error(new SmartQaException(
                    ErrorCode.VALIDATION_FAILED,
                    "Please describe the test you want SmartQA to perform."));
        }
        return resolveProject(request.projectId(), url)
                .flatMap(project -> resolveTestCase(request.testCaseId(), project, url,
                        instructions.isBlank() ? "Structured steps" : instructions)
                        .flatMap(testCase -> {
                            if (hasStructured) {
                                IntentContract contract = StructuredIntentFactory.fromSteps(
                                        testCase.name(),
                                        url,
                                        request.structuredSteps().stream()
                                                .map(step -> new StructuredIntentFactory.StructuredStepInput(
                                                        step.id(), step.action(), step.target(), step.value(),
                                                        step.assertion(), step.location(), step.filter()))
                                                .toList());
                                return intentService.applyStructuredIntent(testCase.id(), contract)
                                        .map(analyzed -> new WorkspaceAnalyzeResponse(project, analyzed));
                            }
                            return intentService.understand(testCase.id())
                                    .map(analyzed -> new WorkspaceAnalyzeResponse(project, analyzed));
                        }));
    }

    private Mono<ProjectResponse> resolveProject(UUID projectId, String url) {
        Mono<ProjectResponse> existing = projectId == null
                ? projectRepository.findByNameOrderByUpdatedAtDesc(WORKSPACE_NAME)
                        .next()
                        .flatMap(project -> projectService.get(project.getId()))
                : projectService.get(projectId).onErrorResume(ignored -> Mono.empty());
        return existing.flatMap(project -> {
                    if (url.equals(project.applicationUrl())) {
                        return Mono.just(project);
                    }
                    return projectService.update(project.id(), new ProjectRequest(
                            project.name(),
                            project.description(),
                            url,
                            project.environment()
                    ));
                })
                .switchIfEmpty(Mono.defer(() -> projectService.create(new ProjectRequest(
                        WORKSPACE_NAME,
                        "Primary Test Generation Workspace",
                        url,
                        "local"
                ))));
    }

    private Mono<TestCaseResponse> resolveTestCase(UUID testCaseId, ProjectResponse project, String url, String instructions) {
        String name = firstLineName(url, instructions);
        Mono<TestCaseResponse> existing = testCaseId == null
                ? Mono.empty()
                : testCaseService.get(testCaseId)
                        .onErrorResume(ignored -> Mono.empty())
                        .filter(testCase -> project.id().equals(testCase.projectId()));
        return existing.flatMap(testCase -> {
                    if (instructions.equals(testCase.naturalLanguage()) && (testCase.name() != null && !testCase.name().isBlank())) {
                        return Mono.just(testCase);
                    }
                    return testCaseService.update(testCase.id(), new TestCaseRequest(
                            testCase.name() == null || testCase.name().isBlank() ? name : testCase.name(),
                            testCase.description(),
                            instructions
                    ));
                })
                .switchIfEmpty(Mono.defer(() -> testCaseService.create(project.id(), new TestCaseRequest(
                        name,
                        "",
                        instructions
                ))));
    }

    private static String firstLineName(String url, String instructions) {
        String first = instructions.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("");
        if (!first.isBlank()) {
            return first.length() > 60 ? first.substring(0, 57) + "..." : first;
        }
        try {
            String host = URI.create(url).getHost();
            return host == null ? "Untitled test" : host.replaceFirst("^www\\.", "");
        } catch (Exception ex) {
            return "Untitled test";
        }
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (Exception ex) {
            return false;
        }
    }
}
