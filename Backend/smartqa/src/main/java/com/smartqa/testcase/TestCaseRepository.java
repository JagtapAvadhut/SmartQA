package com.smartqa.testcase;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TestCaseRepository extends ReactiveCrudRepository<TestCase, UUID> {
    Flux<TestCase> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    @Query("SELECT COUNT(*) FROM test_cases WHERE project_id = :projectId")
    Mono<Long> countByProjectId(UUID projectId);

    @Query("""
            SELECT *
            FROM test_cases
            WHERE project_id = :projectId
              AND id <> :excludeTestCaseId
              AND status IN ('PASSED', 'READY')
              AND locator_memory IS NOT NULL
              AND locator_memory <> ''
            ORDER BY updated_at DESC
            LIMIT 3
            """)
    Flux<TestCase> findRecentMemoryCandidates(UUID projectId, UUID excludeTestCaseId);
}
