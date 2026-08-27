package com.smartqa.clarification;

import com.smartqa.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
public class RuntimeClarificationController {

    private final RuntimeClarificationService clarifications;

    public RuntimeClarificationController(RuntimeClarificationService clarifications) {
        this.clarifications = clarifications;
    }

    @GetMapping("/api/runtime-clarifications/{id}")
    public Mono<ApiResponse<RuntimeClarificationService.RuntimeClarification>> get(@PathVariable UUID id) {
        RuntimeClarificationService.RuntimeClarification found = clarifications.get(id);
        if (found == null) {
            return Mono.just(ApiResponse.fail("Clarification not found", "RESOURCE_NOT_FOUND"));
        }
        return Mono.just(ApiResponse.ok("Clarification", found));
    }

    @PostMapping("/api/runtime-clarifications/{id}/resolve")
    public Mono<ApiResponse<RuntimeClarificationService.RuntimeClarification>> resolve(
            @PathVariable UUID id,
            @RequestBody ResolveRequest request
    ) {
        String selected = request == null ? null : firstNonBlank(request.selectedCandidateId(), request.selectedOption());
        return Mono.just(ApiResponse.ok("Clarification resolved", clarifications.resolve(id, selected)));
    }

    public record ResolveRequest(String selectedCandidateId, String selectedOption) {
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
