package com.shasank.cms_form_extractor.dto;

import java.util.Date;

public class PatientDetails {
    private String firstName;
    private String lastName;
    private String middleInitial;
    private Date dateOfBirth;
    private String gender; // M/F
    private String patientId;

    private String street;
    private String city;
    private String state;
    private String zipCode;

    private String phone;
    private String email;

    private String insurancePlanName;
    private String memberIdNumber;
    private String groupNumber;

    // Getters and Setters
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getMiddleInitial() { return middleInitial; }
    public void setMiddleInitial(String middleInitial) { this.middleInitial = middleInitial; }

    public Date getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(Date dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getInsurancePlanName() { return insurancePlanName; }
    public void setInsurancePlanName(String insurancePlanName) { this.insurancePlanName = insurancePlanName; }

    public String getMemberIdNumber() { return memberIdNumber; }
    public void setMemberIdNumber(String memberIdNumber) { this.memberIdNumber = memberIdNumber; }

    public String getGroupNumber() { return groupNumber; }
    public void setGroupNumber(String groupNumber) { this.groupNumber = groupNumber; }
}
