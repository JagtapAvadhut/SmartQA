package com.smartqa.validation;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ValidationRunRepository extends ReactiveCrudRepository<ValidationRun, UUID> {
    Flux<ValidationRun> findByTestCaseIdOrderByAttemptNumberDesc(UUID testCaseId);
    Mono<Long> countByTestCaseId(UUID testCaseId);
}
