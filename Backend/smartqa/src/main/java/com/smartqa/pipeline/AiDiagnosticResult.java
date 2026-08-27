package com.smartqa.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Strict JSON contract returned by the AI failure diagnostic.
 * Free-form text is never used as a control input.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiDiagnosticResult {

    private String classification;
    private String rootCause;
    private double confidence;
    private String explanation;
    private List<RecoveryOption> recoveryOptions = new ArrayList<>();
    private boolean requiresUserInput;
    private boolean requiresSourceFix;
    private String userQuestion;
    private List<String> userOptions = new ArrayList<>();
    private String assertionSubCategory;
    private String searchSubCategory;
    private String filterSubCategory;
    private String responsibleSubsystem;
    /** Semantic candidate id only (e.g. candidate-B). Never authoritative CSS/XPath. */
    private String recommendedCandidateId;

    public AiDiagnosticResult() {
    }

    public String getClassification() {
        return classification;
    }

    public String classification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getRootCause() {
        return rootCause;
    }

    public String rootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
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

    public String getExplanation() {
        return explanation;
    }

    public String explanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<RecoveryOption> getRecoveryOptions() {
        return recoveryOptions();
    }

    public List<RecoveryOption> recoveryOptions() {
        return recoveryOptions == null ? List.of() : recoveryOptions;
    }

    public void setRecoveryOptions(List<RecoveryOption> recoveryOptions) {
        this.recoveryOptions = recoveryOptions == null ? new ArrayList<>() : new ArrayList<>(recoveryOptions);
    }

    public boolean isRequiresUserInput() {
        return requiresUserInput;
    }

    public boolean requiresUserInput() {
        return requiresUserInput;
    }

    public void setRequiresUserInput(boolean requiresUserInput) {
        this.requiresUserInput = requiresUserInput;
    }

    public boolean isRequiresSourceFix() {
        return requiresSourceFix;
    }

    public boolean requiresSourceFix() {
        return requiresSourceFix;
    }

    public void setRequiresSourceFix(boolean requiresSourceFix) {
        this.requiresSourceFix = requiresSourceFix;
    }

    public String getUserQuestion() {
        return userQuestion;
    }

    public String userQuestion() {
        return userQuestion;
    }

    public void setUserQuestion(String userQuestion) {
        this.userQuestion = userQuestion;
    }

    public List<String> getUserOptions() {
        return userOptions();
    }

    public List<String> userOptions() {
        return userOptions == null ? List.of() : userOptions;
    }

    public void setUserOptions(List<String> userOptions) {
        this.userOptions = userOptions == null ? new ArrayList<>() : new ArrayList<>(userOptions);
    }

    public String getAssertionSubCategory() {
        return assertionSubCategory;
    }

    public String assertionSubCategory() {
        return assertionSubCategory;
    }

    public void setAssertionSubCategory(String assertionSubCategory) {
        this.assertionSubCategory = assertionSubCategory;
    }

    public String getSearchSubCategory() {
        return searchSubCategory;
    }

    public String searchSubCategory() {
        return searchSubCategory;
    }

    public void setSearchSubCategory(String searchSubCategory) {
        this.searchSubCategory = searchSubCategory;
    }

    public String getFilterSubCategory() {
        return filterSubCategory;
    }

    public String filterSubCategory() {
        return filterSubCategory;
    }

    public void setFilterSubCategory(String filterSubCategory) {
        this.filterSubCategory = filterSubCategory;
    }

    public String getResponsibleSubsystem() {
        return responsibleSubsystem;
    }

    public String responsibleSubsystem() {
        return responsibleSubsystem;
    }

    public void setResponsibleSubsystem(String responsibleSubsystem) {
        this.responsibleSubsystem = responsibleSubsystem;
    }

    public String getRecommendedCandidateId() {
        return recommendedCandidateId;
    }

    public String recommendedCandidateId() {
        return recommendedCandidateId;
    }

    public void setRecommendedCandidateId(String recommendedCandidateId) {
        this.recommendedCandidateId = recommendedCandidateId;
    }

    public String normalizedClassification() {
        if (classification == null || classification.isBlank()) {
            return "UNKNOWN";
        }
        return classification.trim().toUpperCase(Locale.ROOT);
    }

    public static AiDiagnosticResult fallback(String classification, String rootCause, String explanation, double confidence) {
        AiDiagnosticResult result = new AiDiagnosticResult();
        result.setClassification(classification);
        result.setRootCause(rootCause);
        result.setExplanation(explanation);
        result.setConfidence(confidence);
        result.setRequiresSourceFix(false);
        result.setRequiresUserInput(false);
        return result;
    }
}
