package com.smartqa.intent;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("intent_reviews")
public class IntentReview {

    @Id
    private UUID id;
    @Column("test_case_id")
    private UUID testCaseId;
    private String status;
    @Column("contract_json")
    private String contractJson;
    @Column("clarifications_json")
    private String clarificationsJson;
    @Column("created_at")
    private LocalDateTime createdAt;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getContractJson() {
        return contractJson;
    }

    public void setContractJson(String contractJson) {
        this.contractJson = contractJson;
    }

    public String getClarificationsJson() {
        return clarificationsJson;
    }

    public void setClarificationsJson(String clarificationsJson) {
        this.clarificationsJson = clarificationsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
