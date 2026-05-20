package com.shasank.cms_form_extractor.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.shasank.cms_form_extractor.config.FormExtractionProperties;
import com.shasank.cms_form_extractor.dto.*;
import com.shasank.cms_form_extractor.util.FileProcessingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.sql.Date;

@Service
public class FormExtractionService {
    private static final Logger logger = LoggerFactory.getLogger(FormExtractionService.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final OllamaClient ollamaClient;
    private final PromptBuilder promptBuilder;
    private final JsonParsingService parsingService;
    private final FileProcessingUtil fileProcessingUtil;
    private final FormExtractionProperties properties;

    public FormExtractionService(OllamaClient ollamaClient,
                                PromptBuilder promptBuilder,
                                JsonParsingService parsingService,
                                FileProcessingUtil fileProcessingUtil,
                                FormExtractionProperties properties) {
        this.ollamaClient = ollamaClient;
        this.promptBuilder = promptBuilder;
        this.parsingService = parsingService;
        this.fileProcessingUtil = fileProcessingUtil;
        this.properties = properties;
    }

    /**
     * Main entry point for form extraction
     */
    public FormExtractionResponse extractFormData(MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();

        logger.info("Starting form extraction for file: {}", file.getOriginalFilename());

        // Step 1: Process file and convert to images
        List<BufferedImage> images = processFile(file);
        if (images.isEmpty()) {
            throw new IOException("No valid images extracted from the file");
        }

        logger.info("Extracted {} images from file", images.size());

        // Step 2: Validate form
        BufferedImage primaryImage = images.get(0);
        boolean isValidForm = validateForm(primaryImage);

        if (!isValidForm) {
            logger.warn("Form validation failed");
            FormExtractionResponse response = new FormExtractionResponse();
            response.setFormValid(false);
            response.setFormType("UNKNOWN");
            response.setConfidenceScore(0.0);
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return response;
        }

        // Step 2.5: Extract metadata from filename
        Map<String, String> fileMetadata = fileProcessingUtil.extractMetadataFromFilename(file.getOriginalFilename());
        logger.info("Extracted metadata from filename: {}", fileMetadata);

        // Step 3: Extract data with agentic loop
        FormExtractionResponse response = extractDataWithAgenticLoop(primaryImage, fileMetadata);

        // Step 4: Enhance extraction accuracy with HyDE if confidence is low
        double threshold = properties.getConfidenceThreshold() != null ? properties.getConfidenceThreshold() : 0.7;
        if (response.getConfidenceScore() < threshold) {
            response = enhanceExtractionWithHyDE(response, primaryImage);
        }

        // Step 5: Final validation and cross-checking
        validateAndCrossCheck(response);

        response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

        logger.info("Form extraction completed in {}ms", response.getProcessingTimeMs());

        return response;
    }

    /**
     * Process file and convert to images
     */
    private List<BufferedImage> processFile(MultipartFile file) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        String fileType = fileProcessingUtil.getFileType(file);

        if ("pdf".equalsIgnoreCase(fileType)) {
            images = fileProcessingUtil.convertPdfToImages(file);
        } else if ("image".equalsIgnoreCase(fileType)) {
            BufferedImage image = fileProcessingUtil.loadImage(file);
            if (image == null) {
                throw new IOException("Could not decode image content from uploaded file");
            }
            images.add(image);
        } else {
            throw new IOException("Unsupported file type: " + fileType);
        }

        return images;
    }

    /**
     * Validate if the form is a valid CMS form
     */
    private boolean validateForm(BufferedImage image) {
        try {
            String base64Image = fileProcessingUtil.imageToBase64(image);
            String prompt = promptBuilder.buildFormValidationPrompt();

            String response = ollamaClient.generateResponse(base64Image, prompt);
            Map<String, Object> validationResult = parsingService.parseValidationResponse(response);

            boolean isValid = (boolean) validationResult.get("isValid");
            logger.info("Form validation result: {}", isValid);

            return isValid;
        } catch (IOException e) {
            logger.error("Error validating form", e);
            return false;
        }
    }

    /**
     * Extract data with agentic loop for refinement
     */
    private FormExtractionResponse extractDataWithAgenticLoop(BufferedImage image, Map<String, String> fileMetadata) throws IOException {
        String base64Image = fileProcessingUtil.imageToBase64(image);
        FormExtractionResponse response = new FormExtractionResponse();
        int maxIterations = properties.getMaxRetries() != null ? properties.getMaxRetries() : 2;
        String metadataJson = gson.toJson(fileMetadata == null ? Map.of() : fileMetadata);

        // Pre-populate known fields from filename metadata
        if (fileMetadata != null && !fileMetadata.isEmpty()) {
            populateFromMetadata(response, fileMetadata);
        }

        // Make a single multimodal pass, then switch to text-only refinement to reduce model crashes/timeouts.
        String evidenceJson = "{}";
        try {
            String evidencePrompt = promptBuilder.buildImageEvidencePrompt(metadataJson);
            evidenceJson = ollamaClient.generateResponse(base64Image, evidencePrompt);
        } catch (IOException e) {
            if (isVisionTensorFailure(e)) {
                logger.warn("Vision model tensor assertion occurred; continuing with metadata + text-only refinement", e);
            } else {
                logger.warn("Image evidence extraction failed; continuing with metadata + text-only refinement", e);
            }
        }

        String hydePrompt = promptBuilder.buildHyDEFromEvidencePrompt(metadataJson, evidenceJson);
        String hydeDraftJson = "{}";
        try {
            hydeDraftJson = ollamaClient.generateTextResponse(hydePrompt);
            FormExtractionResponse hydeDraft = parsingService.parseExtractionResponse(hydeDraftJson);
            mergeExtractionResponses(response, hydeDraft);
        } catch (IOException e) {
            logger.warn("Failed to build HyDE draft from textual evidence", e);
        }

        List<String> sections = List.of(
            "Patient and Insurance",
            "Provider and Physician",
            "Diagnosis Codes",
            "Service Lines and Financials"
        );

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            logger.info("Agent extraction iteration {}/{}", iteration, maxIterations);
            boolean improved = false;

            for (String section : sections) {
                try {
                    String prompt = promptBuilder.buildSectionRefinementPrompt(
                        section,
                        metadataJson,
                        evidenceJson,
                        hydeDraftJson,
                        gson.toJson(response)
                    );
                    String ollamaResponse = ollamaClient.generateTextResponse(prompt);
                    FormExtractionResponse fieldResponse = parsingService.parseExtractionResponse(ollamaResponse);
                    double previous = response.getConfidenceScore();

                    mergeExtractionResponses(response, fieldResponse);
                    if (response.getConfidenceScore() > previous) {
                        improved = true;
                    }
                } catch (IOException e) {
                    logger.warn("Failed to extract section [{}] at iteration {}", section, iteration, e);
                }
            }

            if (response.getConfidenceScore() >= 0.85 || !improved) {
                logger.info("Agent extraction completed at iteration {} with confidence score: {}",
                    iteration, response.getConfidenceScore());
                break;
            }
        }

        return response;
    }

    private boolean isVisionTensorFailure(IOException exception) {
        String message = exception.getMessage();
        return message != null && message.contains("GGML_ASSERT");
    }

    private void populateFromMetadata(FormExtractionResponse response, Map<String, String> metadata) {
        if (response.getProviderDetails() == null) {
            response.setProviderDetails(new ProviderDetails());
        }
        if (metadata.containsKey("providerNPI")) {
            response.getProviderDetails().setProviderNPI(metadata.get("providerNPI"));
            logger.info("Pre-populated provider NPI from filename: {}", metadata.get("providerNPI"));
        }

        if (response.getPatientDetails() == null) {
            response.setPatientDetails(new PatientDetails());
        }
        if (metadata.containsKey("membershipId")) {
            response.getPatientDetails().setMemberIdNumber(metadata.get("membershipId"));
            logger.info("Pre-populated membership ID from filename: {}", metadata.get("membershipId"));
        }

        if (metadata.containsKey("claimDate")) {
            try {
                java.time.LocalDate claimDate = java.time.LocalDate.parse(
                    metadata.get("claimDate"),
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
                );
                response.setServiceStartDate(java.sql.Date.valueOf(claimDate));
                response.setServiceEndDate(java.sql.Date.valueOf(claimDate));
                logger.info("Pre-populated dates from filename: {}", metadata.get("claimDate"));
            } catch (Exception e) {
                logger.debug("Could not parse claim date from filename: {}", metadata.get("claimDate"), e);
            }
        }
    }


    private void mergeExtractionResponses(FormExtractionResponse target, FormExtractionResponse incoming) {
        if (incoming == null) {
            return;
        }

        if (!isBlank(incoming.getFormType())) {
            target.setFormType(incoming.getFormType());
        }

        target.setFormValid(target.isFormValid() || incoming.isFormValid());
        target.setConfidenceScore(Math.max(target.getConfidenceScore(), incoming.getConfidenceScore()));

        if (incoming.getPatientDetails() != null) {
            target.setPatientDetails(mergePatientDetails(target.getPatientDetails(), incoming.getPatientDetails()));
        }
        if (incoming.getProviderDetails() != null) {
            target.setProviderDetails(mergeProviderDetails(target.getProviderDetails(), incoming.getProviderDetails()));
        }
        if (incoming.getPhysicianDetails() != null) {
            target.setPhysicianDetails(mergePhysicianDetails(target.getPhysicianDetails(), incoming.getPhysicianDetails()));
        }

        if (isBetterDiagnosisSet(incoming.getDiagnosisCodes(), target.getDiagnosisCodes())) {
            target.setDiagnosisCodes(incoming.getDiagnosisCodes());
        }
        if (isBetterServiceLineSet(incoming.getServiceLines(), target.getServiceLines())) {
            target.setServiceLines(incoming.getServiceLines());
        }

        if (target.getServiceStartDate() == null && incoming.getServiceStartDate() != null) {
            target.setServiceStartDate(incoming.getServiceStartDate());
        }
        if (target.getServiceEndDate() == null && incoming.getServiceEndDate() != null) {
            target.setServiceEndDate(incoming.getServiceEndDate());
        }

        if (isBlank(target.getClaimNumber()) && !isBlank(incoming.getClaimNumber())) {
            target.setClaimNumber(incoming.getClaimNumber());
        }
        if (isBlank(target.getProviderControlNumber()) && !isBlank(incoming.getProviderControlNumber())) {
            target.setProviderControlNumber(incoming.getProviderControlNumber());
        }

        if (incoming.getNotes() != null && !incoming.getNotes().isEmpty()) {
            List<ExtractionNote> notes = target.getNotes() != null ? target.getNotes() : new ArrayList<>();
            notes.addAll(incoming.getNotes());
            target.setNotes(notes);
        }
    }

    private PatientDetails mergePatientDetails(PatientDetails target, PatientDetails incoming) {
        if (target == null) {
            target = new PatientDetails();
        }
        target.setFirstName(preferNonBlank(target.getFirstName(), incoming.getFirstName()));
        target.setLastName(preferNonBlank(target.getLastName(), incoming.getLastName()));
        target.setMiddleInitial(preferNonBlank(target.getMiddleInitial(), incoming.getMiddleInitial()));
        if (target.getDateOfBirth() == null && incoming.getDateOfBirth() != null) {
            target.setDateOfBirth(incoming.getDateOfBirth());
        }
        target.setGender(preferNonBlank(target.getGender(), incoming.getGender()));
        target.setPatientId(preferNonBlank(target.getPatientId(), incoming.getPatientId()));
        target.setStreet(preferNonBlank(target.getStreet(), incoming.getStreet()));
        target.setCity(preferNonBlank(target.getCity(), incoming.getCity()));
        target.setState(preferNonBlank(target.getState(), incoming.getState()));
        target.setZipCode(preferNonBlank(target.getZipCode(), incoming.getZipCode()));
        target.setPhone(preferNonBlank(target.getPhone(), incoming.getPhone()));
        target.setEmail(preferNonBlank(target.getEmail(), incoming.getEmail()));
        target.setInsurancePlanName(preferNonBlank(target.getInsurancePlanName(), incoming.getInsurancePlanName()));
        target.setMemberIdNumber(preferNonBlank(target.getMemberIdNumber(), incoming.getMemberIdNumber()));
        target.setGroupNumber(preferNonBlank(target.getGroupNumber(), incoming.getGroupNumber()));
        return target;
    }

    private ProviderDetails mergeProviderDetails(ProviderDetails target, ProviderDetails incoming) {
        if (target == null) {
            target = new ProviderDetails();
        }
        target.setProviderName(preferNonBlank(target.getProviderName(), incoming.getProviderName()));
        target.setProviderNPI(preferNonBlank(target.getProviderNPI(), incoming.getProviderNPI()));
        target.setTaxId(preferNonBlank(target.getTaxId(), incoming.getTaxId()));
        target.setStreet(preferNonBlank(target.getStreet(), incoming.getStreet()));
        target.setCity(preferNonBlank(target.getCity(), incoming.getCity()));
        target.setState(preferNonBlank(target.getState(), incoming.getState()));
        target.setZipCode(preferNonBlank(target.getZipCode(), incoming.getZipCode()));
        target.setPhone(preferNonBlank(target.getPhone(), incoming.getPhone()));
        target.setFax(preferNonBlank(target.getFax(), incoming.getFax()));
        return target;
    }

    private PhysicianDetails mergePhysicianDetails(PhysicianDetails target, PhysicianDetails incoming) {
        if (target == null) {
            target = new PhysicianDetails();
        }
        target.setFirstName(preferNonBlank(target.getFirstName(), incoming.getFirstName()));
        target.setLastName(preferNonBlank(target.getLastName(), incoming.getLastName()));
        target.setNpi(preferNonBlank(target.getNpi(), incoming.getNpi()));
        target.setLicenseNumber(preferNonBlank(target.getLicenseNumber(), incoming.getLicenseNumber()));
        target.setSpecialization(preferNonBlank(target.getSpecialization(), incoming.getSpecialization()));
        return target;
    }

    private boolean isBetterDiagnosisSet(List<DiagnosisCode> incoming, List<DiagnosisCode> current) {
        if (incoming == null || incoming.isEmpty()) {
            return false;
        }
        if (current == null || current.isEmpty()) {
            return true;
        }
        return averageDiagnosisConfidence(incoming) >= averageDiagnosisConfidence(current);
    }

    private boolean isBetterServiceLineSet(List<ServiceLine> incoming, List<ServiceLine> current) {
        if (incoming == null || incoming.isEmpty()) {
            return false;
        }
        if (current == null || current.isEmpty()) {
            return true;
        }
        return averageServiceLineConfidence(incoming) >= averageServiceLineConfidence(current);
    }

    private double averageDiagnosisConfidence(List<DiagnosisCode> codes) {
        return codes.stream().mapToDouble(DiagnosisCode::getConfidenceScore).average().orElse(0.0);
    }

    private double averageServiceLineConfidence(List<ServiceLine> lines) {
        return lines.stream().mapToDouble(ServiceLine::getConfidenceScore).average().orElse(0.0);
    }

    private String preferNonBlank(String current, String incoming) {
        return isBlank(current) && !isBlank(incoming) ? incoming : current;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Enhance extraction using HyDE (Hypothetical Document Embeddings) approach
     */
    private FormExtractionResponse enhanceExtractionWithHyDE(FormExtractionResponse response, BufferedImage image) throws IOException {
        logger.info("Applying HyDE enhancement due to low confidence score: {}", response.getConfidenceScore());

        String base64Image = fileProcessingUtil.imageToBase64(image);
        List<ExtractionNote> notes = new ArrayList<>();

        // Verify diagnosis codes
        if (response.getDiagnosisCodes() != null && !response.getDiagnosisCodes().isEmpty()) {
            double threshold = properties.getConfidenceThreshold() != null ? properties.getConfidenceThreshold() : 0.7;
            for (DiagnosisCode code : response.getDiagnosisCodes()) {
                if (code.getConfidenceScore() < threshold) {
                    ExtractionNote note = verifyFieldWithHyDE(base64Image, "Diagnosis Code", code.getCode());
                    notes.add(note);
                }
            }
        }

        // Verify service lines
        if (response.getServiceLines() != null && !response.getServiceLines().isEmpty()) {
            double threshold = properties.getConfidenceThreshold() != null ? properties.getConfidenceThreshold() : 0.7;
            for (ServiceLine line : response.getServiceLines()) {
                if (line.getConfidenceScore() < threshold) {
                    StringBuilder fieldDesc = new StringBuilder();
                    fieldDesc.append("Service Line ").append(line.getLineNumber());
                    if (line.getCptCode() != null) {
                        fieldDesc.append(" CPT: ").append(line.getCptCode());
                    }

                    ExtractionNote note = verifyFieldWithHyDE(base64Image, fieldDesc.toString(),
                                                             gson.toJson(line));
                    notes.add(note);
                }
            }
        }

        response.setNotes(notes);
        return response;
    }

    /**
     * Verify a specific field using HyDE approach
     */
    private ExtractionNote verifyFieldWithHyDE(String base64Image, String fieldName, String extractedValue) throws IOException {
        String prompt = promptBuilder.buildHyDEVerificationPrompt(fieldName, extractedValue);

        String response = ollamaClient.generateResponse(base64Image, prompt);
        Map<String, Object> verification = parseVerificationResponse(response);

        ExtractionNote note = new ExtractionNote();
        note.setFieldName(fieldName);
        note.setOriginalValue(extractedValue);
        note.setConfidenceScore((double) verification.getOrDefault("confidence", 0.5));
        note.setResolution((String) verification.getOrDefault("reasoning", "Manual review recommended"));
        note.setIssue(((boolean) verification.getOrDefault("isValid", true)) ? "VERIFIED" : "AMBIGUOUS");

        List<?> alternatives = (List<?>) verification.get("alternatives");
        if (alternatives != null && !alternatives.isEmpty()) {
            note.setSuggestedValue(String.valueOf(alternatives.get(0)));
        }

        return note;
    }

    /**
     * Parse HyDE verification response
     */
    private Map<String, Object> parseVerificationResponse(String response) {
        Map<String, Object> result = new HashMap<>();

        try {
            int jsonStart = response.indexOf("{");
            int jsonEnd = response.lastIndexOf("}");

            if (jsonStart != -1 && jsonEnd != -1) {
                String jsonStr = response.substring(jsonStart, jsonEnd + 1);
                Map<String, Object> parsed = gson.fromJson(jsonStr, Map.class);
                result.putAll(parsed);
            }
        } catch (Exception e) {
            logger.error("Error parsing verification response", e);
        }

        return result;
    }

    /**
     * Validate extracted data against CMS form standards and cross-check fields
     */
    private void validateAndCrossCheck(FormExtractionResponse response) {
        List<ExtractionNote> notes = response.getNotes();
        if (notes == null) {
            notes = new ArrayList<>();
            response.setNotes(notes);
        }

        // Validate NPI format (10 digits)
        if (response.getProviderDetails() != null && response.getProviderDetails().getProviderNPI() != null) {
            String npi = response.getProviderDetails().getProviderNPI().replaceAll("[^0-9]", "");
            if (npi.length() != 10) {
                ExtractionNote note = new ExtractionNote();
                note.setFieldName("Provider NPI");
                note.setOriginalValue(response.getProviderDetails().getProviderNPI());
                note.setIssue("INVALID_FORMAT");
                note.setResolution("NPI should be 10 digits");
                notes.add(note);
            }
        }

        // Validate ICD-10 codes format
        if (response.getDiagnosisCodes() != null) {
            for (DiagnosisCode code : response.getDiagnosisCodes()) {
                if ("ICD-10".equals(code.getCodeType()) && code.getCode() != null) {
                    // ICD-10 format: X.XX... or XXXXX...
                    if (!code.getCode().matches("[A-Z][0-9]{1,2}(\\.[0-9A-Z]{0,2})?")) {
                        ExtractionNote note = new ExtractionNote();
                        note.setFieldName("ICD-10 Code");
                        note.setOriginalValue(code.getCode());
                        note.setIssue("INVALID_FORMAT");
                        note.setResolution("Expected format: X.XX or similar");
                        notes.add(note);
                    }
                }
            }
        }

        // Validate CPT codes (5 character alphanumeric)
        if (response.getServiceLines() != null) {
            for (ServiceLine line : response.getServiceLines()) {
                if (line.getCptCode() != null && !line.getCptCode().matches("[A-Z0-9]{5}")) {
                    ExtractionNote note = new ExtractionNote();
                    note.setFieldName("CPT Code Line " + line.getLineNumber());
                    note.setOriginalValue(line.getCptCode());
                    note.setIssue("INVALID_FORMAT");
                    note.setResolution("CPT codes should be 5 alphanumeric characters");
                    notes.add(note);
                }
            }
        }

        // Cross-check diagnosis pointers in service lines
        if (response.getDiagnosisCodes() != null && response.getServiceLines() != null) {
            int diagnosisCount = response.getDiagnosisCodes().size();

            for (ServiceLine line : response.getServiceLines()) {
                if (line.getDiagnosisPointers() != null) {
                    for (String pointer : line.getDiagnosisPointers()) {
                        try {
                            int pointerNum = Integer.parseInt(pointer);
                            if (pointerNum > diagnosisCount || pointerNum < 1) {
                                ExtractionNote note = new ExtractionNote();
                                note.setFieldName("Service Line " + line.getLineNumber() + " Diagnosis Pointer");
                                note.setOriginalValue(pointer);
                                note.setIssue("INCONSISTENT_REFERENCE");
                                note.setResolution("Pointer " + pointer + " references non-existent diagnosis");
                                notes.add(note);
                            }
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid pointer value: {}", pointer);
                        }
                    }
                }
            }
        }

        response.setNotes(notes);
    }
}
