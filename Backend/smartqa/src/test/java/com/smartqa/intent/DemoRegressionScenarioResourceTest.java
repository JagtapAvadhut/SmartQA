package com.smartqa.intent;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DemoRegressionScenarioResourceTest {

    @Test
    void benchmarkScenarioResourceExists() {
        InputStream resource = getClass().getResourceAsStream("/benchmarks/demo-regression-scenarios.json");
        assertNotNull(resource, "Benchmark scenario resource should be present for demo regressions");
    }
}

