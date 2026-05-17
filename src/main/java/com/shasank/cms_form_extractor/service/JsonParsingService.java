package com.shasank.cms_form_extractor.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.shasank.cms_form_extractor.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class JsonParsingService {
    private static final Logger logger = LoggerFactory.getLogger(JsonParsingService.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Parse form validation response from Ollama
     */
    public Map<String, Object> parseValidationResponse(String ollamaResponse) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Extract JSON from response (Ollama may include additional text)
            String jsonStr = extractJsonFromResponse(ollamaResponse);
            JsonObject json = gson.fromJson(jsonStr, JsonObject.class);

            result.put("isValid", json.get("isValid").getAsBoolean());
            result.put("formType", json.has("formType") ? json.get("formType").getAsString() : "UNKNOWN");
            result.put("qualityScore", json.has("qualityScore") ? json.get("qualityScore").getAsInt() : 0);
            result.put("recommendation", json.has("recommendation") ? json.get("recommendation").getAsString() : "AUTOMATIC");

            logger.info("Validation parsed - Valid: {}, Type: {}, Quality: {}",
                result.get("isValid"), result.get("formType"), result.get("qualityScore"));

        } catch (JsonSyntaxException e) {
            logger.error("Failed to parse validation response", e);
            result.put("isValid", false);
            result.put("formType", "UNKNOWN");
            result.put("qualityScore", 0);
            result.put("recommendation", "MANUAL_REVIEW");
        }

        return result;
    }

    /**
     * Parse the main data extraction response
     */
    public FormExtractionResponse parseExtractionResponse(String ollamaResponse) {
        FormExtractionResponse response = new FormExtractionResponse();

        try {
            String jsonStr = extractJsonFromResponse(ollamaResponse);
            JsonObject json = gson.fromJson(jsonStr, JsonObject.class);

            // Set form type and validity
            if (json.has("formType")) {
                response.setFormType(json.get("formType").getAsString());
            }
            if (json.has("isValid")) {
                response.setFormValid(json.get("isValid").getAsBoolean());
            }
            if (json.has("confidenceScore")) {
                response.setConfidenceScore(json.get("confidenceScore").getAsDouble());
            }

            // Parse patient details
            if (json.has("patient")) {
                response.setPatientDetails(parsePatientDetails(json.getAsJsonObject("patient")));
            }

            // Parse provider details
            if (json.has("provider")) {
                response.setProviderDetails(parseProviderDetails(json.getAsJsonObject("provider")));
            }

            // Parse physician details
            if (json.has("physician")) {
                response.setPhysicianDetails(parsePhysicianDetails(json.getAsJsonObject("physician")));
            }

            // Parse diagnosis codes
            if (json.has("diagnoses")) {
                response.setDiagnosisCodes(parseDiagnosisCode(json.getAsJsonArray("diagnoses")));
            }

            // Parse service lines
            if (json.has("serviceLines")) {
                response.setServiceLines(parseServiceLines(json.getAsJsonArray("serviceLines")));
            }

            // Parse dates
            if (json.has("serviceStartDate")) {
                response.setServiceStartDate(parseDate(json.get("serviceStartDate").getAsString()));
            }
            if (json.has("serviceEndDate")) {
                response.setServiceEndDate(parseDate(json.get("serviceEndDate").getAsString()));
            }

            // Parse claim info
            if (json.has("claimNumber")) {
                response.setClaimNumber(json.get("claimNumber").getAsString());
            }
            if (json.has("providerControlNumber")) {
                response.setProviderControlNumber(json.get("providerControlNumber").getAsString());
            }

            logger.info("Successfully parsed extraction response");

        } catch (Exception e) {
            logger.error("Error parsing extraction response", e);
            // Return empty response for retry
        }

        return response;
    }

    /**
     * Extract JSON from LLM response (handles cases where JSON is embedded in text)
     */
    private String extractJsonFromResponse(String response) {
        // Try to find JSON object in the response
        int startIdx = response.indexOf("{");
        int endIdx = response.lastIndexOf("}");

        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            return response.substring(startIdx, endIdx + 1);
        }

        return response; // Return as-is and let gson handle the error
    }

    /**
     * Parse patient details from JSON object
     */
    private PatientDetails parsePatientDetails(JsonObject json) {
        PatientDetails patient = new PatientDetails();

        if (json.has("firstName")) patient.setFirstName(json.get("firstName").getAsString());
        if (json.has("lastName")) patient.setLastName(json.get("lastName").getAsString());
        if (json.has("middleInitial")) patient.setMiddleInitial(json.get("middleInitial").getAsString());
        if (json.has("dateOfBirth")) patient.setDateOfBirth(parseDate(json.get("dateOfBirth").getAsString()));
        if (json.has("gender")) patient.setGender(json.get("gender").getAsString());
        if (json.has("patientId")) patient.setPatientId(json.get("patientId").getAsString());

        if (json.has("street")) patient.setStreet(json.get("street").getAsString());
        if (json.has("city")) patient.setCity(json.get("city").getAsString());
        if (json.has("state")) patient.setState(json.get("state").getAsString());
        if (json.has("zipCode")) patient.setZipCode(json.get("zipCode").getAsString());

        if (json.has("phone")) patient.setPhone(json.get("phone").getAsString());
        if (json.has("email")) patient.setEmail(json.get("email").getAsString());

        if (json.has("insurancePlanName")) patient.setInsurancePlanName(json.get("insurancePlanName").getAsString());
        if (json.has("memberIdNumber")) patient.setMemberIdNumber(json.get("memberIdNumber").getAsString());
        if (json.has("groupNumber")) patient.setGroupNumber(json.get("groupNumber").getAsString());

        return patient;
    }

    /**
     * Parse provider details from JSON object
     */
    private ProviderDetails parseProviderDetails(JsonObject json) {
        ProviderDetails provider = new ProviderDetails();

        if (json.has("providerName")) provider.setProviderName(json.get("providerName").getAsString());
        if (json.has("providerNPI")) provider.setProviderNPI(json.get("providerNPI").getAsString());
        if (json.has("taxId")) provider.setTaxId(json.get("taxId").getAsString());

        if (json.has("street")) provider.setStreet(json.get("street").getAsString());
        if (json.has("city")) provider.setCity(json.get("city").getAsString());
        if (json.has("state")) provider.setState(json.get("state").getAsString());
        if (json.has("zipCode")) provider.setZipCode(json.get("zipCode").getAsString());

        if (json.has("phone")) provider.setPhone(json.get("phone").getAsString());
        if (json.has("fax")) provider.setFax(json.get("fax").getAsString());

        return provider;
    }

    /**
     * Parse physician details from JSON object
     */
    private PhysicianDetails parsePhysicianDetails(JsonObject json) {
        PhysicianDetails physician = new PhysicianDetails();

        if (json.has("firstName")) physician.setFirstName(json.get("firstName").getAsString());
        if (json.has("lastName")) physician.setLastName(json.get("lastName").getAsString());
        if (json.has("npi")) physician.setNpi(json.get("npi").getAsString());
        if (json.has("licenseNumber")) physician.setLicenseNumber(json.get("licenseNumber").getAsString());
        if (json.has("specialization")) physician.setSpecialization(json.get("specialization").getAsString());

        return physician;
    }

    /**
     * Parse diagnosis codes from JSON array
     */
    private List<DiagnosisCode> parseDiagnosisCode(com.google.gson.JsonArray array) {
        List<DiagnosisCode> codes = new ArrayList<>();

        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.get(i).getAsJsonObject();
            DiagnosisCode code = new DiagnosisCode();

            if (obj.has("codeType")) code.setCodeType(obj.get("codeType").getAsString());
            if (obj.has("code")) code.setCode(obj.get("code").getAsString());
            if (obj.has("description")) code.setDescription(obj.get("description").getAsString());
            if (obj.has("confidenceScore")) code.setConfidenceScore(obj.get("confidenceScore").getAsDouble());
            if (obj.has("pointerPosition")) code.setPointerPosition(obj.get("pointerPosition").getAsInt());

            codes.add(code);
        }

        return codes;
    }

    /**
     * Parse service lines from JSON array
     */
    private List<ServiceLine> parseServiceLines(com.google.gson.JsonArray array) {
        List<ServiceLine> lines = new ArrayList<>();

        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.get(i).getAsJsonObject();
            ServiceLine line = new ServiceLine();

            line.setLineNumber(obj.has("lineNumber") ? obj.get("lineNumber").getAsInt() : i + 1);

            if (obj.has("dateOfService")) {
                line.setDateOfService(parseDate(obj.get("dateOfService").getAsString()));
            }
            if (obj.has("placeOfService")) line.setPlaceOfService(obj.get("placeOfService").getAsString());

            if (obj.has("cptCode")) line.setCptCode(obj.get("cptCode").getAsString());
            if (obj.has("cptDescription")) line.setCptDescription(obj.get("cptDescription").getAsString());
            if (obj.has("modifier1")) line.setModifier1(obj.get("modifier1").getAsString());
            if (obj.has("modifier2")) line.setModifier2(obj.get("modifier2").getAsString());
            if (obj.has("modifier3")) line.setModifier3(obj.get("modifier3").getAsString());
            if (obj.has("modifier4")) line.setModifier4(obj.get("modifier4").getAsString());

            if (obj.has("diagnosisPointers") && obj.get("diagnosisPointers").isJsonArray()) {
                List<String> pointers = new ArrayList<>();
                for (int p = 0; p < obj.getAsJsonArray("diagnosisPointers").size(); p++) {
                    pointers.add(obj.getAsJsonArray("diagnosisPointers").get(p).getAsString());
                }
                line.setDiagnosisPointers(pointers);
            }

            if (obj.has("chargedAmount")) line.setChargedAmount(obj.get("chargedAmount").getAsDouble());
            if (obj.has("quantity")) line.setQuantity(obj.get("quantity").getAsInt());
            if (obj.has("units")) line.setUnits(obj.get("units").getAsString());

            if (obj.has("allowedAmount")) line.setAllowedAmount(obj.get("allowedAmount").getAsDouble());
            if (obj.has("deductible")) line.setDeductible(obj.get("deductible").getAsDouble());
            if (obj.has("coinsurance")) line.setCoinsurance(obj.get("coinsurance").getAsDouble());
            if (obj.has("copay")) line.setCopay(obj.get("copay").getAsDouble());
            if (obj.has("balance")) line.setBalance(obj.get("balance").getAsDouble());

            if (obj.has("confidenceScore")) line.setConfidenceScore(obj.get("confidenceScore").getAsDouble());

            lines.add(line);
        }

        return lines;
    }

    /**
     * Parse date from string (handles multiple formats)
     */
    private Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ISO_LOCAL_DATE
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                LocalDate localDate = LocalDate.parse(dateStr, formatter);
                return java.sql.Date.valueOf(localDate);
            } catch (Exception e) {
                // Try next formatter
            }
        }

        logger.warn("Could not parse date: {}", dateStr);
        return null;
    }
}
