# CMS Form Extractor - Documentation

## Overview

CMS Form Extractor is a Spring Boot application that uses Ollama's Qwen2.5-VL (Vision Language Model) to intelligently extract structured data from medical claim forms (CMS-1500 HCFA and UB-04 CMS-1450). The system handles scanned copies, photographic images, skewed documents, overlapping fields, and poor quality captures using advanced LLM capabilities with HyDE (Hypothetical Document Embeddings) and agentic loops for accuracy refinement.

## Features

- **Multi-Format Support**: Accepts PDF documents and image files (PNG, JPG, BMP, GIF)
- **Form Type Detection**: Automatically identifies CMS-1500 (HCFA) or UB-04 (CMS-1450) forms
- **Intelligent Extraction**: Uses Qwen2.5-VL vision model via Ollama for accurate data recognition
- **Agentic Loop Refinement**: Multi-pass extraction with automatic refinement for better accuracy
- **HyDE Verification**: Hypothetical document embeddings approach for ambiguous field verification
- **Comprehensive Parsing**: Extracts:
  - Patient demographics and insurance information
  - Provider and physician details
  - Diagnosis codes (ICD-10, ICD-9)
  - Service line items with CPT codes, modifiers, and financial details
  - Claim-level information
  
- **Quality Assessment**: Analyzes form quality and provides confidence scores
- **Ambiguity Handling**: Flags and resolves ambiguous extractions with detailed notes
- **Format Validation**: Validates extracted codes (NPI, ICD-10, CPT) against standards
- **Batch Processing**: Support for processing multiple forms in one request
- **Comprehensive API**: RESTful endpoints for extraction and health checks

## Prerequisites

### System Requirements
- Java 25 or higher
- Maven 3.6+
- Ollama running locally with Qwen2.5-VL model

### Setting up Ollama

1. **Install Ollama**
   - Download from: https://ollama.ai
   - Follow installation instructions for your OS

2. **Pull Qwen2.5-VL Model**
   ```bash
   ollama pull qwen2.5-vl-instruct
   ```

3. **Start Ollama Service**
   - By default, Ollama runs on http://localhost:11434
   - Verify with: `curl http://localhost:11434/api/tags`

## Installation & Setup

### 1. Clone and Build

```bash
cd cms-form-extractor
mvn clean install
```

### 2. Configure Application

Edit `src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: cms-form-extractor
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB

server:
  port: 8080

ollama:
  base-url: http://localhost:11434
  model: qwen2.5-vl-instruct
  timeout-seconds: 120

form:
  extraction:
    max-retries: 3
    confidence-threshold: 0.7
```

### 3. Run the Application

```bash
mvn spring-boot:run
```

Or run the packaged JAR:

```bash
java -jar target/cms-form-extractor-0.0.1-SNAPSHOT.jar
```

The application will start on port 8080.

## API Endpoints

### 1. Extract Form Data

**Endpoint**: `POST /api/v1/forms/extract`

**Request**:
```bash
curl -X POST \
  -F "file=@/path/to/form.pdf" \
  http://localhost:8080/api/v1/forms/extract
```

**Response** (JSON):
```json
{
  "formType": "CMS-1500",
  "formValid": true,
  "confidenceScore": 0.92,
  "patientDetails": {
    "firstName": "John",
    "lastName": "Doe",
    "dateOfBirth": "1965-01-15",
    "gender": "M",
    "street": "123 Main St",
    "city": "Springfield",
    "state": "IL",
    "zipCode": "62701",
    "phone": "217-555-0147",
    "insurancePlanName": "Blue Cross",
    "memberIdNumber": "BCM123456789",
    "groupNumber": "GROUP001"
  },
  "providerDetails": {
    "providerName": "Main Medical Clinic",
    "providerNPI": "1234567890",
    "taxId": "12-3456789",
    "street": "456 Provider Ave",
    "city": "Springfield",
    "state": "IL",
    "zipCode": "62702",
    "phone": "217-555-0200",
    "fax": "217-555-0201"
  },
  "physicianDetails": {
    "firstName": "Jane",
    "lastName": "Smith",
    "npi": "9876543210",
    "licenseNumber": "IL123456",
    "specialization": "Family Medicine"
  },
  "diagnosisCodes": [
    {
      "codeType": "ICD-10",
      "code": "E11.9",
      "description": "Type 2 diabetes mellitus without complications",
      "confidenceScore": 0.95,
      "pointerPosition": 1
    },
    {
      "codeType": "ICD-10",
      "code": "I10",
      "description": "Essential hypertension",
      "confidenceScore": 0.93,
      "pointerPosition": 2
    }
  ],
  "serviceLines": [
    {
      "lineNumber": 1,
      "dateOfService": "2024-03-15",
      "placeOfService": "11",
      "cptCode": "99213",
      "cptDescription": "Office visit, established patient",
      "diagnosisPointers": ["1", "2"],
      "chargedAmount": 150.00,
      "quantity": 1,
      "confidenceScore": 0.94
    }
  ],
  "notes": [
    {
      "fieldName": "Group Number",
      "issue": "LOW_CONFIDENCE",
      "originalValue": "GROUP001",
      "confidenceScore": 0.72,
      "resolution": "Value extracted but confidence is below threshold"
    }
  ],
  "processingTimeMs": 8234
}
```

### 2. Batch Extract Multiple Forms

**Endpoint**: `POST /api/v1/forms/extract-batch`

**Request**:
```bash
curl -X POST \
  -F "files=@form1.pdf" \
  -F "files=@form2.pdf" \
  -F "files=@form3.jpg" \
  http://localhost:8080/api/v1/forms/extract-batch
```

**Response**:
```json
{
  "status": "success",
  "totalFiles": 3,
  "processedFiles": 3,
  "failedFiles": 0,
  "results": [
    { /* FormExtractionResponse 1 */ },
    { /* FormExtractionResponse 2 */ },
    { /* FormExtractionResponse 3 */ }
  ]
}
```

### 3. Health Check

**Endpoint**: `GET /api/v1/forms/health`

**Response**:
```json
{
  "status": "UP",
  "service": "CMS Form Extractor",
  "version": "1.0.0"
}
```

### 4. API Information

**Endpoint**: `GET /api/v1/forms/info`

**Response**:
```json
{
  "service": "CMS Form Extraction API",
  "version": "1.0.0",
  "description": "Extracts structured data from CMS-1500 (HCFA) and UB-04 (CMS-1450) forms using Ollama Qwen2.5-VL",
  "endpoints": {
    "POST /api/v1/forms/extract": "Extract form data from PDF or image file",
    "GET /api/v1/forms/health": "Health check endpoint",
    "GET /api/v1/forms/info": "API information and available endpoints"
  },
  "supportedFormats": {
    "forms": "CMS-1500 (HCFA), UB-04 (CMS-1450)",
    "fileTypes": "PDF, PNG, JPG, BMP, GIF",
    "maxFileSize": "100MB"
  }
}
```

## Processing Workflow

### Stage 1: Form Validation
- Identifies form type (CMS-1500 or UB-04)
- Assesses image quality
- Checks for common capture issues

### Stage 2: Hierarchical Extraction
1. **Priority 1**: Patient demographics and insurance
2. **Priority 2**: Provider and physician information
3. **Priority 3**: Diagnosis codes
4. **Priority 4**: Service line items
5. **Priority 5**: Financial information

### Stage 3: Data Validation
- Validates code formats (NPI = 10 digits, ICD-10 with period, CPT = 5 chars)
- Cross-references diagnosis pointers with service lines
- Verifies date consistency

### Stage 4: Confidence Scoring
- Analyzes text clarity (printed vs handwritten)
- Verifies field positions
- Checks format compliance
- Validates cross-field consistency

### Stage 5: Agentic Loop Refinement
- Performs multiple extraction passes if confidence is low
- Refines understanding based on previous attempts
- Validates extracted data against form standards

### Stage 6: HyDE Enhancement (if needed)
- Applies Hypothetical Document Embeddings approach
- Verifies ambiguous fields
- Suggests alternatives for low-confidence extractions

## Extracted Data Structure

### FormExtractionResponse
- `formType`: "CMS-1500" or "UB-04" or "UNKNOWN"
- `formValid`: Boolean indicating if form is properly recognized
- `confidenceScore`: 0-1 overall confidence
- `patientDetails`: PatientDetails object
- `providerDetails`: ProviderDetails object
- `physicianDetails`: PhysicianDetails object
- `diagnosisCodes`: List of DiagnosisCode objects
- `serviceLines`: List of ServiceLine objects
- `notes`: List of ExtractionNote objects (for ambiguous fields)
- `processingTimeMs`: Time taken to extract

### PatientDetails
- Personal: firstName, lastName, middleInitial, dateOfBirth, gender, patientId
- Address: street, city, state, zipCode
- Contact: phone, email
- Insurance: insurancePlanName, memberIdNumber, groupNumber

### ProviderDetails
- providerName, providerNPI, taxId
- Address: street, city, state, zipCode
- Contact: phone, fax

### PhysicianDetails
- firstName, lastName, npi, licenseNumber, specialization

### DiagnosisCode
- codeType: "ICD-10" or "ICD-9"
- code: The diagnosis code
- description: English description
- confidenceScore: 0-1
- pointerPosition: Link to service lines

### ServiceLine
- Line information: lineNumber, dateOfService, placeOfService
- Procedure: cptCode, cptDescription, modifier1-4
- References: diagnosisPointers
- Amounts: chargedAmount, allowedAmount, quantity, units
- Insurance: deductible, coinsurance, copay, balance
- confidenceScore: 0-1

### ExtractionNote
- fieldName: Name of the field with issues
- issue: "AMBIGUOUS", "LOW_CONFIDENCE", "INVALID_FORMAT", etc.
- originalValue: Extracted value
- suggestedValue: Alternative suggestion if available
- confidenceScore: 0-1
- resolution: Explanation or recommendation

## Error Handling

The API returns appropriate HTTP status codes:

- `200 OK`: Successful extraction
- `400 Bad Request`: Invalid file or parameters
- `500 Internal Server Error`: Server-side processing error

Error responses include descriptive messages to help troubleshoot issues.

## Performance Considerations

- **Processing Time**: Depends on form complexity and Ollama model speed
  - Simple forms: 5-15 seconds
  - Complex forms with low quality: 20-60 seconds
  - Batch processing: Linear with number of forms
  
- **File Size**: Default max 100MB per file
- **Timeouts**: Ollama timeout set to 120 seconds per request

## Troubleshooting

### Ollama Connection Issues
- Verify Ollama is running: `curl http://localhost:11434/api/tags`
- Check firewall settings
- Ensure correct base URL in application.yaml

### Model Not Found
- Pull model: `ollama pull qwen2.5-vl-instruct`
- Verify model is available: `ollama list`

### Low Confidence Scores
- Try higher quality image (better resolution, less skew)
- Ensure proper lighting on photographed forms
- Consider pre-processing images before upload

### Memory Issues
- Adjust JVM heap size: `java -Xmx4g -jar`
- Reduce batch size in batch processing

## Development

### Project Structure
```
src/main/java/com/shasank/cms_form_extractor/
├── controller/          # REST API endpoints
├── service/            # Business logic
│   ├── FormExtractionService.java
│   ├── OllamaClient.java
│   ├── PromptBuilder.java
│   └── JsonParsingService.java
├── util/               # Utility classes
│   └── FileProcessingUtil.java
├── config/             # Configuration classes
│   ├── OllamaProperties.java
│   └── FormExtractionProperties.java
└── dto/                # Data Transfer Objects
    └── [various DTOs]
```

### Adding New Form Types

1. Extend `FormExtractionService`
2. Add form-type specific prompts in `PromptBuilder`
3. Create corresponding DTO classes
4. Update validation logic

## Future Enhancements

- Support for additional form types (CMS-1450, etc.)
- Database persistence of extracted forms
- User authentication and authorization
- Form validation rules engine
- Machine learning model fine-tuning
- Frontend UI for visualization
- Export to standard healthcare formats (HL7, FHIR)

## License

This project is provided as-is for healthcare form extraction purposes.

## Support

For issues or questions, refer to logs in the application console or enable debug logging in application.yaml.
