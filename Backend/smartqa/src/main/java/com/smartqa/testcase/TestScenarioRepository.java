package com.smartqa.testcase;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TestScenarioRepository extends ReactiveCrudRepository<TestScenario, UUID> {
    Flux<TestScenario> findByTestCaseIdOrderByScenarioOrderAsc(UUID testCaseId);

    Mono<Void> deleteByTestCaseId(UUID testCaseId);
}
