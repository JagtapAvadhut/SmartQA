package com.smartqa.testcase;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("test_cases")
public class TestCase {

    @Id
    private UUID id;
    @Column("project_id")
    private UUID projectId;
    private String name;
    private String description;
    private String status;
    @Column("natural_language")
    private String naturalLanguage;
    @Column("generated_code")
    private String generatedCode;
    @Column("locator_memory")
    private String locatorMemory;
    @Column("intent_contract")
    private String intentContract;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNaturalLanguage() {
        return naturalLanguage;
    }

    public void setNaturalLanguage(String naturalLanguage) {
        this.naturalLanguage = naturalLanguage;
    }

    public String getGeneratedCode() {
        return generatedCode;
    }

    public void setGeneratedCode(String generatedCode) {
        this.generatedCode = generatedCode;
    }

    public String getLocatorMemory() {
        return locatorMemory;
    }

    public void setLocatorMemory(String locatorMemory) {
        this.locatorMemory = locatorMemory;
    }

    public String getIntentContract() {
        return intentContract;
    }

    public void setIntentContract(String intentContract) {
        this.intentContract = intentContract;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
