package com.shasank.cms_form_extractor.dto;

public class ExtractionNote {
    private String fieldName;
    private String issue; // "AMBIGUOUS", "LOW_CONFIDENCE", "SKEWED", "OVERLAPPING"
    private String originalValue;
    private String suggestedValue;
    private double confidenceScore;
    private String resolution; // How it was resolved or needs manual review

    // Getters and Setters
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getIssue() { return issue; }
    public void setIssue(String issue) { this.issue = issue; }

    public String getOriginalValue() { return originalValue; }
    public void setOriginalValue(String originalValue) { this.originalValue = originalValue; }

    public String getSuggestedValue() { return suggestedValue; }
    public void setSuggestedValue(String suggestedValue) { this.suggestedValue = suggestedValue; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
