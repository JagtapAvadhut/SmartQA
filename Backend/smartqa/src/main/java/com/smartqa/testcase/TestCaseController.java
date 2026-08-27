package com.smartqa.testcase;

import com.smartqa.common.api.ApiResponse;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
public class TestCaseController {

    private final TestCaseService testCaseService;

    public TestCaseController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @GetMapping("/api/projects/{projectId}/test-cases")
    public Mono<ApiResponse<List<TestCaseResponse>>> list(@PathVariable UUID projectId) {
        return testCaseService.listByProject(projectId).collectList()
                .map(items -> ApiResponse.ok("Test cases fetched", items));
    }

    @PostMapping("/api/projects/{projectId}/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<TestCaseResponse>> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody TestCaseRequest request) {
        int instructionLength = request == null || request.naturalLanguage() == null ? 0 : request.naturalLanguage().length();
        TraceLogger.info("CONTROLLER", "ENTER", "createTestCase", TraceMeta.of(
                "projectId", projectId.toString(),
                "instructionLength", instructionLength
        ));
        return testCaseService.create(projectId, request)
                .map(item -> ApiResponse.ok("Test case created", item));
    }

    @GetMapping("/api/test-cases/{id}")
    public Mono<ApiResponse<TestCaseResponse>> get(@PathVariable UUID id) {
        return testCaseService.get(id)
                .map(item -> ApiResponse.ok("Test case fetched", item));
    }

    @PutMapping("/api/test-cases/{id}")
    public Mono<ApiResponse<TestCaseResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody TestCaseRequest request) {
        return testCaseService.update(id, request)
                .map(item -> ApiResponse.ok("Test case updated", item));
    }

    @DeleteMapping("/api/test-cases/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable UUID id) {
        return testCaseService.delete(id)
                .thenReturn(ApiResponse.ok("Test case deleted", null));
    }
}
