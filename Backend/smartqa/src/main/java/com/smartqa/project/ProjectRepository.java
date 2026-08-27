package com.smartqa.project;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ProjectRepository extends ReactiveCrudRepository<Project, UUID> {
    Flux<Project> findByNameOrderByUpdatedAtDesc(String name);
}
