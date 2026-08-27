package com.smartqa.testcase;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("test_steps")
public class TestStep {

    @Id
    private UUID id;
    @Column("scenario_id")
    private UUID scenarioId;
    @Column("step_order")
    private int stepOrder;
    @Column("step_text")
    private String stepText;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(UUID scenarioId) {
        this.scenarioId = scenarioId;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(int stepOrder) {
        this.stepOrder = stepOrder;
    }

    public String getStepText() {
        return stepText;
    }

    public void setStepText(String stepText) {
        this.stepText = stepText;
    }
}
