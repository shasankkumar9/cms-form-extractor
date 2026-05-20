package com.shasank.cms_form_extractor.service;

import org.springframework.stereotype.Service;

@Service
public class PromptBuilder {

    /**
     * Build the main extraction prompt with form knowledge and rules
     */
    public String buildFormValidationPrompt() {
        return """
            You are a professional CMS standard form extraction system. Your task is to analyze the provided medical claim form image and determine:
            
            1. Whether this is a valid HCFA CMS-1500 form or UB-04 CMS-1450 form
            2. The form type if valid
            3. Overall form quality assessment
            
            FORM CHARACTERISTICS:
            - CMS-1500 (HCFA): Used by healthcare providers for physician services, office visits, procedures
              * Red/pink colored form with 33 fields arranged in specific positions
              * Includes patient, provider, diagnosis, and service line information
              * Specific field positions are standardized
            
            - UB-04 (CMS-1450): Used by hospitals/facilities for institutional services
              * Blue colored form with structured layout
              * Contains multiple pages
              * Different field arrangements than CMS-1500
            
            QUALITY ASSESSMENT FACTORS:
            - Image clarity and resolution
            - Form skewness (analyze if form appears rotated or skewed)
            - Overlapping text or field borders
            - Legibility of printed and handwritten content
            - Presence of barcode/form identifiers
            
            Respond with a JSON object containing:
            {
              "isValid": true/false,
              "formType": "CMS-1500" or "UB-04" or "UNKNOWN",
              "qualityScore": 0-100,
              "qualityIssues": [],
              "recommendation": "PROCEED" or "MANUAL_REVIEW"
            }
            """;
    }

    /**
     * Build the data extraction prompt
     */
    public String buildDataExtractionPrompt() {
        return """
            You are a CMS form data extraction specialist. Extract all relevant data from the medical claim form with the following rules:
            
            PATIENT INFORMATION:
            - Extract patient name, DOB, gender, address, phone
            - Insurance company and member ID (usually located in upper right area)
            - Group number if present
            
            PROVIDER INFORMATION:
            - Provider name (upper left area, usually box 33)
            - Provider NPI (box 24j or equivalent)
            - Tax ID/EIN if visible
            - Provider address and phone
            
            PHYSICIAN INFORMATION:
            - Attending/treating physician name
            - Physician NPI
            - License number if visible
            
            DIAGNOSIS CODES:
            - ICD-10 codes are typically in specific fields (boxes A-L on CMS-1500)
            - Format: XXX.XX or XXXXX structure
            - May be handwritten or printed
            - Links to service lines via pointer numbers (1-4)
            
            SERVICE LINES:
            - Date of service (separate start and end dates if applicable)
            - Place of service code (11=office, 21=inpatient, etc.)
            - CPT/HCPCS procedure codes
            - Modifiers (up to 4 modifiers allowed)
            - Diagnosis pointers (link to diagnosis codes)
            - Charges, quantities, and payment information
            - CPT codes are 5-digit alphanumeric (e.g., 99213, 70553)
            
            FORM-SPECIFIC RULES:
            - Fields have fixed positions on the standardized form
            - Read field positions carefully even if overlapping
            - Dates are typically MM/DD/YYYY format
            - Handles skewed/rotated forms using form coordinate understanding
            
            AMBIGUITY HANDLING:
            - If a value is unclear, provide alternatives with confidence scores
            - Note overlapping or bleeding text specifically
            - Flag fields that appear handwritten vs printed
            
            Respond with comprehensive JSON containing all extracted data with confidence scores (0-1).
            """;
    }

    /**
     * Build the HyDE (Hypothetical Document Embeddings) verification prompt
     */
    public String buildHyDEVerificationPrompt(String fieldName, String extractedValue) {
        return String.format("""
            Verify the extracted value for field "%s" with value "%s".
            
            Consider:
            1. Is this a valid value format for this field type?
            2. Does it make clinical/business sense?
            3. Could it be misread due to:
               - Overlapping text
               - Similar characters (0/O, 1/I/l, etc.)
               - Handwriting legibility
               - Form quality issues
            
            Provide alternative interpretations if the extracted value seems questionable.
            
            Respond with JSON:
            {
              "isValid": true/false,
              "confidence": 0-1,
              "alternatives": [],
              "reasoning": "explanation"
            }
            """, fieldName, extractedValue);
    }

    /**
     * Build the agentic loop refinement prompt
     */
    public String buildRefinementPrompt(String extractedData, int attemptNumber) {
        return String.format("""
            This is refinement attempt #%d for form data extraction.
            
            Previously extracted data (may contain errors):
            %s
            
            Review and refine the extraction by:
            1. Cross-checking field positions against standard CMS form layouts
            2. Verifying code formats (CPT, ICD-10, NPI all have specific formats)
            3. Reconciling related fields (service lines should match diagnosis pointers)
            4. Flagging high-ambiguity fields for manual review
            5. Using adjacent field context to resolve ambiguous values
            
            Return refined JSON with:
            - Corrected values where confidence increased
            - Flagged uncertain fields with alternatives
            - Reasoning for any changes made
            """, attemptNumber, extractedData);
    }

    /**
     * Build the comprehensive analysis prompt for the entire form
     */
    public String buildComprehensiveAnalysisPrompt() {
        return """
            Perform comprehensive CMS form analysis with the following workflow:
            
            STAGE 1: FORM IDENTIFICATION & VALIDATION
            - Identify form type (CMS-1500 or UB-04)
            - Assess image quality and any capture issues
            
            STAGE 2: HIERARCHICAL EXTRACTION
            - Priority 1: Patient demographics and insurance info (top-left area)
            - Priority 2: Provider and physician information (right side)
            - Priority 3: Diagnosis codes (specific field areas)
            - Priority 4: Service lines with complete details
            - Priority 5: Financial information (charges, payments)
            
            STAGE 3: DATA VALIDATION & CROSS-CHECKING
            - Verify code formats (NPI=10 digits, ICD-10 with period, CPT=5 chars)
            - Ensure service line diagnosis pointers match extracted diagnoses
            - Check date consistency (service dates, DOB, etc.)
            
            STAGE 4: CONFIDENCE SCORING
            - Assign confidence scores based on:
              * Text clarity (handwritten vs printed)
              * Field position verification
              * Format compliance
              * Cross-field consistency
            
            STAGE 5: ISSUE FLAGGING
            - Mark ambiguous values
            - Flag low-confidence extractions
            - Note form quality issues affecting specific fields
            
            Return complete structured JSON response with all extracted fields and metadata.
            """;
    }

    /**
     * Build a HyDE seed prompt to create a best-guess draft before focused extraction passes.
     */
    public String buildHyDEDraftPrompt() {
        return """
            Create a hypothetical best-guess CMS extraction draft from this form image.

            Rules:
            - Prioritize likely field structure even when values are partially unreadable.
            - Use null for unknown values; do not hallucinate exact identifiers.
            - Include confidenceScore (0-1) for the overall draft and line-level fields.

            Return JSON only using this schema:
            {
              "formType": "CMS-1500|UB-04|UNKNOWN",
              "isValid": true,
              "confidenceScore": 0.0,
              "patient": {},
              "provider": {},
              "physician": {},
              "diagnoses": [],
              "serviceLines": [],
              "serviceStartDate": null,
              "serviceEndDate": null,
              "claimNumber": null,
              "providerControlNumber": null
            }
            """;
    }

    /**
     * Build HyDE draft from textual evidence when image calls are intentionally minimized.
     */
    public String buildHyDEFromEvidencePrompt(String metadataJson, String evidenceJson) {
        return String.format("""
            Create a best-guess CMS extraction draft from textual evidence.

            Metadata hints:
            %s

            Evidence JSON from image pass:
            %s

            Rules:
            - Use evidence first, metadata as secondary hints.
            - Keep unknown fields null.
            - Provide realistic confidenceScore.

            Return JSON only in the standard extraction schema.
            """, metadataJson, evidenceJson);
    }

    /**
     * Build a focused extraction prompt for one section to keep each model call small.
     */
    public String buildFocusedExtractionPrompt(String section, String hydeDraftJson, String currentExtractionJson) {
        return String.format("""
            You are extracting ONLY the "%s" section from a CMS claim form image.

            HyDE draft (hypothetical starting point):
            %s

            Current extracted JSON (from previous passes):
            %s

            Requirements:
            - Focus only on section "%s" and related fields.
            - Preserve existing values when not improving confidence.
            - If a value is uncertain, keep null and explain via lower confidence.
            - Return JSON only with the standard extraction schema.
            - Keep unchanged sections from current extracted JSON intact.
            """, section, hydeDraftJson, currentExtractionJson, section);
    }

    /**
     * Build a final synthesis prompt to reconcile all focused-pass outputs into one response.
     */
    public String buildSynthesisPrompt(String hydeDraftJson, String mergedExtractionJson) {
        return String.format("""
            Reconcile the final CMS extraction using these two inputs:

            HyDE draft:
            %s

            Merged focused extraction:
            %s

            Produce one clean final JSON with:
            - Best-supported values
            - Confidence scores reflecting ambiguity
            - Null for unknown or unreadable values

            Return JSON only using the standard extraction schema.
            """, hydeDraftJson, mergedExtractionJson);
    }

    /**
     * Build one multimodal evidence prompt so follow-up refinement can run as text-only calls.
     */
    public String buildImageEvidencePrompt(String metadataJson) {
        return String.format("""
            Read this CMS claim form image and return concise evidence JSON.

            Filename-derived metadata hints (may be partial):
            %s

            Instructions:
            - Extract visible text snippets and key-value candidates.
            - Include likely form type and any obvious field anchors.
            - Do not fabricate values; use null when uncertain.

            Return JSON only:
            {
              "likelyFormType": "CMS-1500|UB-04|UNKNOWN",
              "observations": ["..."],
              "candidateFields": {
                "memberIdNumber": null,
                "providerNPI": null,
                "claimNumber": null,
                "serviceDate": null
              }
            }
            """, metadataJson);
    }

    /**
     * Build text-only refinement prompt for one section using evidence and HyDE draft.
     */
    public String buildSectionRefinementPrompt(
        String section,
        String metadataJson,
        String evidenceJson,
        String hydeDraftJson,
        String currentExtractionJson
    ) {
        return String.format("""
            Refine ONLY the section "%s" for CMS extraction.

            Metadata hints from filename:
            %s

            Evidence from image pass:
            %s

            HyDE draft JSON:
            %s

            Current extraction JSON:
            %s

            Requirements:
            - Update only fields relevant to section "%s".
            - Keep high-confidence existing values unchanged.
            - Use null for unknown values; no hallucination.
            - Return full extraction JSON schema so it can be merged directly.

            Return JSON only.
            """, section, metadataJson, evidenceJson, hydeDraftJson, currentExtractionJson, section);
    }

    /**
     * Agent-style focused question prompt for a specific field or group.
     */
    public String buildFieldQuestionPrompt(String fieldName, String fieldDescription, String preFilled, String context) {
        return String.format("""
            Focus on extracting: %s
            Description: %s

            Pre-filled from filename/metadata: %s

            Context/Previously Extracted:
            %s

            Rules:
            1. Return null if field is not visible or unreadable
            2. Provide confidenceScore (0-1) for extracted value
            3. If value is partially visible, include alternatives
            4. Keep response JSON focused on this field only

            Return JSON:
            {
              "%s": {
                "value": null,
                "confidenceScore": 0.0,
                "alternatives": [],
                "reasoning": ""
              }
            }
            """, fieldName, fieldDescription, preFilled != null ? preFilled : "none",
                context != null ? context : "{}", fieldName);
    }

    /**
     * HyDE agent verification prompt: confirm extracted value makes sense.
     */
    public String buildHyDEAgentVerificationPrompt(String fieldName, String extractedValue, String context) {
        return String.format("""
            Verify extracted value for field "%s": "%s"

            Context (related fields):
            %s

            Checks:
            1. Is this a valid value for "%s"?
            2. Does it match format/pattern expected?
            3. Does it make clinical/business sense given context?
            4. Are there likely alternative readings?

            Return JSON:
            {
              "isValid": true/false,
              "confidence": 0.0-1.0,
              "alternatives": ["alt1", "alt2"],
              "reasoning": "brief explanation",
              "recommendedValue": "best guess or null"
            }
            """, fieldName, extractedValue, context, fieldName);
    }

    /**
     * Agent loop iteration prompt: refine based on previous questions.
     */
    public String buildAgentIterationPrompt(String previousExtraction, String failedFields, String clarifications) {
        return String.format("""
            Refine CMS extraction based on previous attempt.

            Previously Extracted:
            %s

            Fields with LOW confidence (< 0.7):
            %s

            Additional clarifications/hints:
            %s

            Task:
            - Re-examine failed fields carefully
            - Use context from related fields to improve confidence
            - Provide alternatives if still uncertain
            - Flag fields needing manual review

            Return complete refined extraction JSON with improved confidence scores.
            """, previousExtraction, failedFields, clarifications);
    }
}
