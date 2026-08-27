package com.smartqa.testcase;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("test_scenarios")
public class TestScenario {

    @Id
    private UUID id;
    @Column("test_case_id")
    private UUID testCaseId;
    @Column("scenario_name")
    private String scenarioName;
    @Column("scenario_order")
    private int scenarioOrder;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(UUID testCaseId) {
        this.testCaseId = testCaseId;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public int getScenarioOrder() {
        return scenarioOrder;
    }

    public void setScenarioOrder(int scenarioOrder) {
        this.scenarioOrder = scenarioOrder;
    }
}
