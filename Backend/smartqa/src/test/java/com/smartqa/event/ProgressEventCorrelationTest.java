package com.smartqa.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressEventCorrelationTest {

    @AfterEach
    void clear() {
        RunCorrelation.clear();
    }

    @Test
    void generationFactoryKeepsDetailsGenerationRunId() {
        UUID generationRunId = UUID.randomUUID();
        ProgressEvent event = ProgressEvent.generation(
                "GENERATION_STARTED",
                "Starting generation",
                UUID.randomUUID(),
                java.util.Map.of("generationRunId", generationRunId.toString()));
        assertEquals(generationRunId.toString(), event.details().get("generationRunId"));
    }

    @Test
    void hubOverwritesUndefinedGenerationRunId() {
        UUID pipeline = UUID.randomUUID();
        UUID generation = UUID.randomUUID();
        UUID testCase = UUID.randomUUID();
        RunCorrelation.set(pipeline, generation, testCase);
        ProgressEventHub hub = new ProgressEventHub();
        String channel = ProgressEventHub.generationChannel(testCase);
        hub.emit(channel, ProgressEvent.generation(
                "GENERATION_STARTED",
                "Starting generation",
                testCase,
                java.util.Map.of("generationRunId", "undefined", "pipelineRunId", "undefined")));
        java.util.concurrent.atomic.AtomicReference<ProgressEvent> seen = new java.util.concurrent.atomic.AtomicReference<>();
        hub.stream(channel).take(1).subscribe(seen::set);
        org.junit.jupiter.api.Assertions.assertNotNull(seen.get());
        assertEquals(generation.toString(), seen.get().details().get("generationRunId"));
        assertEquals(pipeline.toString(), seen.get().details().get("pipelineRunId"));
        assertEquals(testCase.toString(), seen.get().details().get("testCaseId"));
    }

    @Test
    void locatorSelectedCanCarryEvidenceMomentId() {
        ProgressEvent event = ProgressEvent.generation(
                "LOCATOR_SELECTED",
                "Selected locator",
                UUID.randomUUID(),
                java.util.Map.of("evidenceMomentId", "moment-9", "path", "SEMANTIC_INTENT"));
        assertEquals("moment-9", event.details().get("evidenceMomentId"));
        assertEquals("SEMANTIC_INTENT", event.details().get("path"));
    }

    @Test
    void hubKeepsNullGenerationRunIdUntilCreated() {
        ProgressEventHub hub = new ProgressEventHub();
        UUID testCase = UUID.randomUUID();
        String channel = ProgressEventHub.generationChannel(testCase);
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("generationRunId", null);
        hub.emit(channel, ProgressEvent.generation(
                "PIPELINE_STARTED",
                "Starting autonomous test pipeline",
                testCase,
                details));
        java.util.concurrent.atomic.AtomicReference<ProgressEvent> seen = new java.util.concurrent.atomic.AtomicReference<>();
        hub.stream(channel).take(1).subscribe(seen::set);
        org.junit.jupiter.api.Assertions.assertNotNull(seen.get());
        org.junit.jupiter.api.Assertions.assertTrue(seen.get().details().containsKey("generationRunId"));
        org.junit.jupiter.api.Assertions.assertNull(seen.get().details().get("generationRunId"));
        org.junit.jupiter.api.Assertions.assertNotEquals("undefined", String.valueOf(seen.get().details().get("generationRunId")));
    }

    @Test
    void correlationStoresGenerationAndPipelineIds() {
        UUID pipeline = UUID.randomUUID();
        UUID generation = UUID.randomUUID();
        RunCorrelation.set(pipeline, generation);
        assertEquals(pipeline, RunCorrelation.pipelineRunId());
        assertEquals(generation, RunCorrelation.generationRunId());
    }
}
