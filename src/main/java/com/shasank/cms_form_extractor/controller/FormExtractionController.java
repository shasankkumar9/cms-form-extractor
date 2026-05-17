package com.shasank.cms_form_extractor.controller;

import com.shasank.cms_form_extractor.dto.FormExtractionResponse;
import com.shasank.cms_form_extractor.service.FormExtractionService;
import com.shasank.cms_form_extractor.service.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/forms")
@CrossOrigin(origins = "*", maxAge = 3600)
public class FormExtractionController {
    private static final Logger logger = LoggerFactory.getLogger(FormExtractionController.class);

    private final FormExtractionService formExtractionService;
    private final OllamaClient ollamaClient;

    public FormExtractionController(FormExtractionService formExtractionService, OllamaClient ollamaClient) {
        this.formExtractionService = formExtractionService;
        this.ollamaClient = ollamaClient;
    }

    /**
     * Extract CMS form data from uploaded file (PDF or Image)
     */
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> extractForm(
            @RequestParam("file") MultipartFile file) {

        try {
            if (file.isEmpty()) {
                logger.warn("Empty file uploaded");
                return ResponseEntity.badRequest().body(error("EMPTY_FILE", "Uploaded file is empty"));
            }

            String filename = file.getOriginalFilename();
            String contentType = file.getContentType();

            logger.info("Received form extraction request for file: {}, content-type: {}", filename, contentType);

            // Validate file type
            if (!isValidFileType(contentType, filename)) {
                logger.warn("Invalid file type: {}", contentType);
                return ResponseEntity.badRequest().body(error("INVALID_FILE_TYPE", "Only PDF and image files are supported"));
            }

            FormExtractionResponse response = formExtractionService.extractFormData(file);

            logger.info("Form extraction completed with status - Valid: {}, Type: {}, Confidence: {}",
                response.isFormValid(), response.getFormType(), response.getConfidenceScore());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            logger.error("Error processing file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("PROCESSING_ERROR", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error during form extraction", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("UNEXPECTED_ERROR", e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        boolean ollamaHealthy = ollamaClient.healthCheck();
        response.put("status", ollamaHealthy ? "UP" : "DEGRADED");
        response.put("service", "CMS Form Extractor");
        response.put("version", "1.0.0");
        response.put("ollama", ollamaHealthy ? "UP" : "DOWN_OR_MODEL_MISSING");

        return ResponseEntity.ok(response);
    }

    /**
     * Get API information and supported endpoints
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> apiInfo() {
        Map<String, Object> info = new HashMap<>();

        info.put("service", "CMS Form Extraction API");
        info.put("version", "1.0.0");
        info.put("description", "Extracts structured data from CMS-1500 (HCFA) and UB-04 (CMS-1450) forms using Ollama Qwen2.5-VL");

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("POST /api/v1/forms/extract", "Extract form data from PDF or image file");
        endpoints.put("GET /api/v1/forms/health", "Health check endpoint");
        endpoints.put("GET /api/v1/forms/info", "API information and available endpoints");

        info.put("endpoints", endpoints);

        Map<String, String> supportedFormats = new HashMap<>();
        supportedFormats.put("forms", "CMS-1500 (HCFA), UB-04 (CMS-1450)");
        supportedFormats.put("fileTypes", "PDF, PNG, JPG, BMP, GIF");
        supportedFormats.put("maxFileSize", "100MB");

        info.put("supportedFormats", supportedFormats);

        Map<String, Object> responseFields = new HashMap<>();
        responseFields.put("formType", "Type of form detected (CMS-1500, UB-04, UNKNOWN)");
        responseFields.put("formValid", "Whether the form is valid/recognized");
        responseFields.put("confidenceScore", "Overall confidence score (0-1)");
        responseFields.put("patientDetails", "Extracted patient information");
        responseFields.put("providerDetails", "Extracted provider information");
        responseFields.put("physicianDetails", "Extracted physician information");
        responseFields.put("diagnosisCodes", "ICD-10 codes with confidence scores");
        responseFields.put("serviceLines", "Service line items with CPT codes");
        responseFields.put("notes", "Extraction notes for ambiguous fields");
        responseFields.put("processingTimeMs", "Time taken to process the form");

        info.put("responseFields", responseFields);

        return ResponseEntity.ok(info);
    }

    /**
     * Batch extract multiple forms
     */
    @PostMapping(value = "/extract-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> extractBatch(
            @RequestParam("files") MultipartFile[] files) {

        Map<String, Object> batchResponse = new HashMap<>();

        try {
            if (files == null || files.length == 0) {
                batchResponse.put("status", "error");
                batchResponse.put("message", "No files provided");
                return ResponseEntity.badRequest().body(batchResponse);
            }

            logger.info("Received batch extraction request for {} files", files.length);

            java.util.List<FormExtractionResponse> responses = new java.util.ArrayList<>();
            java.util.List<String> errors = new java.util.ArrayList<>();

            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];

                try {
                    if (file != null && !file.isEmpty() && isValidFileType(file.getContentType(), file.getOriginalFilename())) {
                        FormExtractionResponse response = formExtractionService.extractFormData(file);
                        responses.add(response);
                        logger.info("Successfully processed file {} of {}: {}", i + 1, files.length, file.getOriginalFilename());
                    } else {
                        String errorMsg = String.format("File %d (%s): invalid or empty file", i + 1,
                                file != null ? file.getOriginalFilename() : "null");
                        errors.add(errorMsg);
                    }
                } catch (Exception e) {
                    String errorMsg = String.format("File %d (%s): %s", i + 1, file.getOriginalFilename(), e.getMessage());
                    errors.add(errorMsg);
                    logger.error("Error processing file in batch", e);
                }
            }

            batchResponse.put("status", errors.isEmpty() ? "success" : "partial");
            batchResponse.put("totalFiles", files.length);
            batchResponse.put("processedFiles", responses.size());
            batchResponse.put("failedFiles", errors.size());
            batchResponse.put("results", responses);
            if (!errors.isEmpty()) {
                batchResponse.put("errors", errors);
            }

            return ResponseEntity.ok(batchResponse);

        } catch (Exception e) {
            logger.error("Error in batch processing", e);
            batchResponse.put("status", "error");
            batchResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(batchResponse);
        }
    }

    /**
     * Validate file type
     */
    private boolean isValidFileType(String contentType, String filename) {
        if (contentType == null || filename == null) {
            return false;
        }

        // Check content type
        if (contentType.contains("pdf") ||
            contentType.startsWith("image/")) {
            return true;
        }

        // Check file extension
        String lowerFilename = filename.toLowerCase();
        return lowerFilename.endsWith(".pdf") ||
               lowerFilename.endsWith(".jpg") ||
               lowerFilename.endsWith(".jpeg") ||
               lowerFilename.endsWith(".png") ||
               lowerFilename.endsWith(".bmp") ||
               lowerFilename.endsWith(".gif");
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", code);
        body.put("message", message);
        return body;
    }
}
