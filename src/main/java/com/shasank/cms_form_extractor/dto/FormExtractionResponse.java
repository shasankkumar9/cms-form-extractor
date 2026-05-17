package com.shasank.cms_form_extractor.dto;

import java.util.Date;
import java.util.List;

public class FormExtractionResponse {
    private String formType; // CMS-1500 or UB-04
    private boolean formValid;
    private double confidenceScore;

    private PatientDetails patientDetails;
    private ProviderDetails providerDetails;
    private PhysicianDetails physicianDetails;

    private List<DiagnosisCode> diagnosisCodes;
    private List<ServiceLine> serviceLines;

    private Date serviceStartDate;
    private Date serviceEndDate;

    private String claimNumber;
    private String providerControlNumber;

    private List<ExtractionNote> notes;

    private long processingTimeMs;

    // Constructors
    public FormExtractionResponse() {
    }

    public FormExtractionResponse(String formType, boolean formValid, double confidenceScore) {
        this.formType = formType;
        this.formValid = formValid;
        this.confidenceScore = confidenceScore;
    }

    // Getters and Setters
    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public boolean isFormValid() {
        return formValid;
    }

    public void setFormValid(boolean formValid) {
        this.formValid = formValid;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public PatientDetails getPatientDetails() {
        return patientDetails;
    }

    public void setPatientDetails(PatientDetails patientDetails) {
        this.patientDetails = patientDetails;
    }

    public ProviderDetails getProviderDetails() {
        return providerDetails;
    }

    public void setProviderDetails(ProviderDetails providerDetails) {
        this.providerDetails = providerDetails;
    }

    public PhysicianDetails getPhysicianDetails() {
        return physicianDetails;
    }

    public void setPhysicianDetails(PhysicianDetails physicianDetails) {
        this.physicianDetails = physicianDetails;
    }

    public List<DiagnosisCode> getDiagnosisCodes() {
        return diagnosisCodes;
    }

    public void setDiagnosisCodes(List<DiagnosisCode> diagnosisCodes) {
        this.diagnosisCodes = diagnosisCodes;
    }

    public List<ServiceLine> getServiceLines() {
        return serviceLines;
    }

    public void setServiceLines(List<ServiceLine> serviceLines) {
        this.serviceLines = serviceLines;
    }

    public Date getServiceStartDate() {
        return serviceStartDate;
    }

    public void setServiceStartDate(Date serviceStartDate) {
        this.serviceStartDate = serviceStartDate;
    }

    public Date getServiceEndDate() {
        return serviceEndDate;
    }

    public void setServiceEndDate(Date serviceEndDate) {
        this.serviceEndDate = serviceEndDate;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public void setClaimNumber(String claimNumber) {
        this.claimNumber = claimNumber;
    }

    public String getProviderControlNumber() {
        return providerControlNumber;
    }

    public void setProviderControlNumber(String providerControlNumber) {
        this.providerControlNumber = providerControlNumber;
    }

    public List<ExtractionNote> getNotes() {
        return notes;
    }

    public void setNotes(List<ExtractionNote> notes) {
        this.notes = notes;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
}
