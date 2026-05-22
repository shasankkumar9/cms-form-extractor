package com.shasank.cms_form_extractor.service;

import org.springframework.stereotype.Service;

@Service
public class PromptBuilder {

    /**
     * Build the main extraction prompt with form knowledge and rules
     * OPTIMIZED for smaller models: Shorter prompts, fewer requirements
     */
    public String buildFormValidationPrompt() {
        return """
            Analyze this medical claim form image. Is it a valid CMS-1500 or UB-04 form?
            
            CMS-1500: Red/pink form, 33 fields, for physician services
            UB-04: Blue form, for hospital services
            
            Return JSON:
            {
              "isValid": true/false,
              "formType": "CMS-1500|UB-04|UNKNOWN",
              "qualityScore": 0-100
            }
            """;
    }

    /**
     * Build the data extraction prompt
     * OPTIMIZED for smaller models: Simplified extraction requests
     */
    public String buildDataExtractionPrompt() {
        return """
            Extract patient, provider, physician, diagnosis codes, and service lines from this CMS form.
            
            PATIENT: name, DOB, gender, insurance ID
            PROVIDER: name, NPI (10 digits), address
            PHYSICIAN: name, NPI
            DIAGNOSIS: ICD-10 codes (format: X.XX)
            SERVICE LINES: date, CPT code (5 chars), charges
            
            Return JSON with confidence scores 0-1.
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
     * OPTIMIZED: Removed stages, focus on key data only
     */
    public String buildComprehensiveAnalysisPrompt() {
        return """
            Extract from CMS form: patient info, provider (NPI), physician, diagnosis codes (ICD-10), service lines (date, CPT, charges).
            
            Return JSON with all fields and confidence scores (0-1). Mark uncertain fields with lower confidence, use null for unknown.
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

    // ===== NEW OPTIMIZED PROMPTS FOR SMALL MODELS (qwen2.5vl:3b) =====

    /**
     * Extract ONLY patient demographics - minimal context
     */
    public String buildFastPatientExtractionPrompt() {
        return """
            Extract ONLY patient data:
            - First name, last name, DOB (MM/DD/YYYY), gender
            - Address, phone, email (if visible)
            - Insurance ID, group number
            
            Return JSON for patient section only. Use null for missing fields.
            Confidence scores 0-1.
            """;
    }

    /**
     * Extract ONLY provider information - minimal context
     */
    public String buildFastProviderExtractionPrompt() {
        return """
            Extract ONLY provider data:
            - Provider name
            - NPI (10 digits)
            - Tax ID/EIN
            - Address, phone, fax
            
            Return JSON for provider section only. Use null for missing fields.
            Confidence scores 0-1.
            """;
    }

    /**
     * Extract ONLY physician information
     */
    public String buildFastPhysicianExtractionPrompt() {
        return """
            Extract ONLY physician data:
            - First name, last name
            - NPI (10 digits)
            - License number, specialization
            
            Return JSON for physician section only. Use null for missing fields.
            Confidence scores 0-1.
            """;
    }

    /**
     * Extract ONLY diagnosis codes
     */
    public String buildFastDiagnosisExtractionPrompt() {
        return """
            Extract ONLY diagnosis codes:
            - ICD-10 format (e.g., E11.22, M54.5)
            - Code type, description
            - Pointer numbers (1-4 if applicable)
            
            Return JSON for diagnoses list only. Use null for missing fields.
            Confidence scores 0-1.
            """;
    }

    /**
     * Extract ONLY service lines - one at a time for clarity
     */
    public String buildFastServiceLineExtractionPrompt() {
        return """
            Extract ONLY service line data:
            - Line number, date of service (MM/DD/YYYY)
            - Place of service code
            - CPT/HCPCS code (5 characters)
            - Modifiers (if any)
            - Diagnosis pointers
            - Units, charges
            
            Return JSON for serviceLines list only. Use null for missing fields.
            Confidence scores 0-1.
            """;
    }

    /**
     * Verify single field with minimal context
     */
    public String buildFastFieldVerificationPrompt(String fieldName, String extractedValue) {
        return String.format("""
            Is "%s" a valid value for %s?
            
            Return JSON:
            {
              "isValid": true/false,
              "confidence": 0.0-1.0,
              "suggestedValue": "correct value or null"
            }
            """, extractedValue, fieldName);
    }

    /**
     * Quick refinement for low-confidence fields
     */
    public String buildFastRefinementPrompt(String fieldName, String currentValue, String confidence) {
        return String.format("""
            Recheck %s (current: "%s", confidence: %s).
            Is there a better reading?
            
            Return JSON:
            {
              "value": "best reading or null",
              "confidence": 0.0-1.0,
              "reasoning": "brief note"
            }
            """, fieldName, currentValue, confidence);
    }
}


