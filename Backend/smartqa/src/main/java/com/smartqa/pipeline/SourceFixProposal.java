package com.smartqa.pipeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Proposal for a Cursor/development source fix when a generic engine defect is detected.
 * Never hot-edits the running JVM.
 */
public class SourceFixProposal {

    private String id = UUID.randomUUID().toString();
    private String component;
    private String className;
    private String method;
    private String rootCause;
    private String evidence;
    private List<String> affectedTests = new ArrayList<>();
    private String recommendedChange;
    private String regressionTest;
    private String status = "PROPOSED";
    private Instant createdAt = Instant.now();
    private Instant appliedAt;
    private String rebuildLog;
    private boolean applied;

    public SourceFixProposal() {
    }

    public static SourceFixProposal of(
            String component,
            String className,
            String method,
            String rootCause,
            String evidence,
            List<String> affectedTests,
            String recommendedChange,
            String regressionTest) {
        SourceFixProposal p = new SourceFixProposal();
        p.component = component;
        p.className = className;
        p.method = method;
        p.rootCause = rootCause;
        p.evidence = evidence;
        p.affectedTests = affectedTests == null ? new ArrayList<>() : new ArrayList<>(affectedTests);
        p.recommendedChange = recommendedChange;
        p.regressionTest = regressionTest;
        return p;
    }

    public String id() { return id; }
    public void setId(String id) { this.id = id; }
    public String component() { return component; }
    public void setComponent(String component) { this.component = component; }
    public String className() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String method() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String rootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String evidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public List<String> affectedTests() { return affectedTests == null ? List.of() : affectedTests; }
    public void setAffectedTests(List<String> affectedTests) {
        this.affectedTests = affectedTests == null ? new ArrayList<>() : new ArrayList<>(affectedTests);
    }
    public String recommendedChange() { return recommendedChange; }
    public void setRecommendedChange(String recommendedChange) { this.recommendedChange = recommendedChange; }
    public String regressionTest() { return regressionTest; }
    public void setRegressionTest(String regressionTest) { this.regressionTest = regressionTest; }
    public String status() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant createdAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant appliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    public String rebuildLog() { return rebuildLog; }
    public void setRebuildLog(String rebuildLog) { this.rebuildLog = rebuildLog; }
    public boolean applied() { return applied; }
    public void setApplied(boolean applied) { this.applied = applied; }
}
