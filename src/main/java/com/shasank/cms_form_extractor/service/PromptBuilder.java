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
}
