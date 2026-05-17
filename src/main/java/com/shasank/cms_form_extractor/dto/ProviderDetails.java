package com.shasank.cms_form_extractor.dto;

public class ProviderDetails {
    private String providerName;
    private String providerNPI;
    private String taxId;

    private String street;
    private String city;
    private String state;
    private String zipCode;

    private String phone;
    private String fax;

    // Getters and Setters
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public String getProviderNPI() { return providerNPI; }
    public void setProviderNPI(String providerNPI) { this.providerNPI = providerNPI; }

    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }

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

    public String getFax() { return fax; }
    public void setFax(String fax) { this.fax = fax; }
}
