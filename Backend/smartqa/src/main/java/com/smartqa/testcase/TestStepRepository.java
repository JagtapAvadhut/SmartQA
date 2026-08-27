package com.smartqa.testcase;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TestStepRepository extends ReactiveCrudRepository<TestStep, UUID> {
    Flux<TestStep> findByScenarioIdOrderByStepOrderAsc(UUID scenarioId);

    Mono<Void> deleteByScenarioId(UUID scenarioId);
}
