package com.shasank.cms_form_extractor.dto;

import java.util.Date;
import java.util.List;

public class ServiceLine {
    private int lineNumber;

    private Date dateOfService;
    private String placeOfService; // 11, 21, 22, etc.

    private String cptCode;
    private String cptDescription;
    private String modifier1;
    private String modifier2;
    private String modifier3;
    private String modifier4;

    private List<String> diagnosisPointers; // Links to diagnosis codes

    private double chargedAmount;
    private int quantity;
    private String units;

    private double allowedAmount;
    private double deductible;
    private double coinsurance;
    private double copay;
    private double balance;

    private double confidenceScore;

    // Getters and Setters
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public Date getDateOfService() { return dateOfService; }
    public void setDateOfService(Date dateOfService) { this.dateOfService = dateOfService; }

    public String getPlaceOfService() { return placeOfService; }
    public void setPlaceOfService(String placeOfService) { this.placeOfService = placeOfService; }

    public String getCptCode() { return cptCode; }
    public void setCptCode(String cptCode) { this.cptCode = cptCode; }

    public String getCptDescription() { return cptDescription; }
    public void setCptDescription(String cptDescription) { this.cptDescription = cptDescription; }

    public String getModifier1() { return modifier1; }
    public void setModifier1(String modifier1) { this.modifier1 = modifier1; }

    public String getModifier2() { return modifier2; }
    public void setModifier2(String modifier2) { this.modifier2 = modifier2; }

    public String getModifier3() { return modifier3; }
    public void setModifier3(String modifier3) { this.modifier3 = modifier3; }

    public String getModifier4() { return modifier4; }
    public void setModifier4(String modifier4) { this.modifier4 = modifier4; }

    public List<String> getDiagnosisPointers() { return diagnosisPointers; }
    public void setDiagnosisPointers(List<String> diagnosisPointers) { this.diagnosisPointers = diagnosisPointers; }

    public double getChargedAmount() { return chargedAmount; }
    public void setChargedAmount(double chargedAmount) { this.chargedAmount = chargedAmount; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }

    public double getAllowedAmount() { return allowedAmount; }
    public void setAllowedAmount(double allowedAmount) { this.allowedAmount = allowedAmount; }

    public double getDeductible() { return deductible; }
    public void setDeductible(double deductible) { this.deductible = deductible; }

    public double getCoinsurance() { return coinsurance; }
    public void setCoinsurance(double coinsurance) { this.coinsurance = coinsurance; }

    public double getCopay() { return copay; }
    public void setCopay(double copay) { this.copay = copay; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
}
