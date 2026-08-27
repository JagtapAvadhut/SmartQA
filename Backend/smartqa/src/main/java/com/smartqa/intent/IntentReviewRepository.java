package com.smartqa.intent;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface IntentReviewRepository extends ReactiveCrudRepository<IntentReview, UUID> {
    Flux<IntentReview> findByTestCaseIdOrderByCreatedAtDesc(UUID testCaseId);
}
