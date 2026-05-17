package com.shasank.cms_form_extractor.dto;

public class DiagnosisCode {
    private String codeType; // ICD-10, ICD-9
    private String code;
    private String description;
    private double confidenceScore;
    private int pointerPosition; // Position in the form (e.g., 1st, 2nd diagnosis)

    // Getters and Setters
    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public int getPointerPosition() { return pointerPosition; }
    public void setPointerPosition(int pointerPosition) { this.pointerPosition = pointerPosition; }
}
