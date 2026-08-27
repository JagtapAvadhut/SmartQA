package com.smartqa.pipeline;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PipelineRunRepository extends ReactiveCrudRepository<PipelineRunEntity, UUID> {

    Mono<PipelineRunEntity> findFirstByTestCaseIdOrderByStartedAtDesc(UUID testCaseId);
}
