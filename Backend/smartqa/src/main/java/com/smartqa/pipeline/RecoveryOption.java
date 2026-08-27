package com.smartqa.pipeline;

/**
 * One AI-recommended recovery strategy. Not executed until validated as safe.
 */
public class RecoveryOption {

    private String type;
    private String reason;
    private boolean safe = true;
    private double confidence;
    private String targetHint;
    private String domainHint;

    public RecoveryOption() {
    }

    public RecoveryOption(String type, String reason, boolean safe) {
        this.type = type;
        this.reason = reason;
        this.safe = safe;
    }

    public String getType() {
        return type;
    }

    public String type() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public String reason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isSafe() {
        return safe;
    }

    public boolean safe() {
        return safe;
    }

    public void setSafe(boolean safe) {
        this.safe = safe;
    }

    public double getConfidence() {
        return confidence;
    }

    public double confidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getTargetHint() {
        return targetHint;
    }

    public String targetHint() {
        return targetHint;
    }

    public void setTargetHint(String targetHint) {
        this.targetHint = targetHint;
    }

    public String getDomainHint() {
        return domainHint;
    }

    public String domainHint() {
        return domainHint;
    }

    public void setDomainHint(String domainHint) {
        this.domainHint = domainHint;
    }
}
