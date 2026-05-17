package com.shasank.cms_form_extractor.dto;

public class PhysicianDetails {
    private String firstName;
    private String lastName;
    private String npi;
    private String licenseNumber;
    private String specialization;

    // Getters and Setters
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getNpi() { return npi; }
    public void setNpi(String npi) { this.npi = npi; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
}
