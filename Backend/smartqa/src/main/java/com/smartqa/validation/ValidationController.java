package com.smartqa.validation;

import com.smartqa.common.api.ApiResponse;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.event.ProgressEvent;
import com.smartqa.event.ProgressEventHub;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
public class ValidationController {

    private final GeneratedTestValidator validator;
    private final ProgressEventHub eventHub;

    public ValidationController(GeneratedTestValidator validator, ProgressEventHub eventHub) {
        this.validator = validator;
        this.eventHub = eventHub;
    }

    @PostMapping("/api/test-cases/{id}/validate")
    public Mono<ApiResponse<ValidationRunResponse>> validate(@PathVariable UUID id) {
        TraceLogger.info("CONTROLLER", "ENTER", "validate", TraceMeta.of("testCaseId", id.toString()));
        return validator.validate(id)
                .map(item -> ApiResponse.ok("Validation started", item));
    }

    @GetMapping("/api/test-cases/{id}/validations")
    public Mono<ApiResponse<List<ValidationRunResponse>>> history(@PathVariable UUID id) {
        return validator.history(id).collectList()
                .map(list -> ApiResponse.ok("Validation history fetched", list));
    }

    @GetMapping("/api/validation-runs/{id}")
    public Mono<ApiResponse<ValidationRunResponse>> get(@PathVariable UUID id) {
        return validator.get(id)
                .map(item -> ApiResponse.ok("Validation run fetched", item));
    }

    @GetMapping(path = "/api/validation-runs/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ProgressEvent>> stream(
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam(value = "lastEventId", required = false) Long lastEventId) {
        Flux<ServerSentEvent<ProgressEvent>> events = eventHub.stream(ProgressEventHub.validationChannel(id), lastEventId)
                .map(event -> ServerSentEvent.builder(event).event(event.type()).build());
        Flux<ServerSentEvent<ProgressEvent>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<ProgressEvent>builder().comment("ping").build());
        return Flux.merge(events, heartbeat);
    }
}
