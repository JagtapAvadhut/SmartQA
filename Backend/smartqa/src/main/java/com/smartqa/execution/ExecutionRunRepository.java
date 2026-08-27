package com.smartqa.execution;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ExecutionRunRepository extends ReactiveCrudRepository<ExecutionRun, UUID> {
    Flux<ExecutionRun> findByTestCaseIdOrderByCreatedAtDesc(UUID testCaseId);
}
