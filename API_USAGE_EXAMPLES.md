# CMS Form Extractor - API Usage Examples

This guide provides practical examples of how to use the CMS Form Extractor API.

## Prerequisites

1. Ollama is running: `ollama serve`
2. Qwen2.5-VL model is installed: `ollama pull qwen2.5-vl-instruct`
3. Spring Boot application is running: `java -jar target/cms-form-extractor-0.0.1-SNAPSHOT.jar`

## Basic Examples

### 1. Extract a Single Form (PDF)

```bash
curl -X POST \
  -H "Content-Type: multipart/form-data" \
  -F "file=@/path/to/cms_form.pdf" \
  http://localhost:8080/api/v1/forms/extract | jq .
```

### 2. Extract a Single Form (Image)

```bash
curl -X POST \
  -H "Content-Type: multipart/form-data" \
  -F "file=@/path/to/cms_form.jpg" \
  http://localhost:8080/api/v1/forms/extract | jq .
```

### 3. Batch Extract Multiple Forms

```bash
curl -X POST \
  -H "Content-Type: multipart/form-data" \
  -F "files=@form1.pdf" \
  -F "files=@form2.pdf" \
  -F "files=@form3.jpg" \
  http://localhost:8080/api/v1/forms/extract-batch | jq .
```

### 4. Health Check

```bash
curl -X GET http://localhost:8080/api/v1/forms/health | jq .
```

### 5. Get API Information

```bash
curl -X GET http://localhost:8080/api/v1/forms/info | jq .
```

## Python Examples

### Using Python Requests Library

```python
import requests
import json

# Single form extraction
url = "http://localhost:8080/api/v1/forms/extract"

with open('cms_form.pdf', 'rb') as f:
    files = {'file': f}
    response = requests.post(url, files=files)
    
result = response.json()
print(json.dumps(result, indent=2))

# Access specific data
if result['formValid']:
    print(f"Form Type: {result['formType']}")
    print(f"Confidence: {result['confidenceScore']}")
    print(f"Patient: {result['patientDetails']['firstName']} {result['patientDetails']['lastName']}")
    
    for diag in result['diagnosisCodes']:
        print(f"Diagnosis: {diag['code']} - {diag['description']}")
```

### Batch Processing with Python

```python
import requests
import json
import os
from pathlib import Path

url = "http://localhost:8080/api/v1/forms/extract-batch"

# Get all PDF files in a directory
form_files = list(Path('./forms').glob('*.pdf'))

files = [('files', open(f, 'rb')) for f in form_files]

response = requests.post(url, files=files)
result = response.json()

print(f"Status: {result['status']}")
print(f"Processed: {result['processedFiles']}/{result['totalFiles']}")

# Save results to file
with open('extraction_results.json', 'w') as f:
    json.dump(result['results'], f, indent=2, default=str)

# Close all files
for _, f in files:
    f.close()
```

## JavaScript/Node.js Examples

### Using Axios

```javascript
const axios = require('axios');
const FormData = require('form-data');
const fs = require('fs');

async function extractForm(filePath) {
    const formData = new FormData();
    formData.append('file', fs.createReadStream(filePath));
    
    try {
        const response = await axios.post(
            'http://localhost:8080/api/v1/forms/extract',
            formData,
            {
                headers: formData.getHeaders()
            }
        );
        
        console.log('Extraction Result:');
        console.log(JSON.stringify(response.data, null, 2));
        
        if (response.data.formValid) {
            console.log(`\nPatient: ${response.data.patientDetails.firstName} ${response.data.patientDetails.lastName}`);
            console.log(`Provider: ${response.data.providerDetails.providerName}`);
            console.log(`Confidence: ${(response.data.confidenceScore * 100).toFixed(2)}%`);
        }
    } catch (error) {
        console.error('Error:', error.message);
    }
}

// Usage
extractForm('./cms_form.pdf');
```

## Parsing Response Data

### Extracting Patient Information

```bash
curl -s -X POST -F "file=@form.pdf" http://localhost:8080/api/v1/forms/extract | \
  jq '.patientDetails | {name: "\(.firstName) \(.lastName)", dob: .dateOfBirth, insurance: .insurancePlanName}'
```

Output:
```json
{
  "name": "John Doe",
  "dob": "1965-01-15",
  "insurance": "Blue Cross"
}
```

### Extracting Diagnosis Codes

```bash
curl -s -X POST -F "file=@form.pdf" http://localhost:8080/api/v1/forms/extract | \
  jq '.diagnosisCodes[] | {code: .code, description: .description, confidence: .confidenceScore}'
```

Output:
```json
{
  "code": "E11.9",
  "description": "Type 2 diabetes mellitus without complications",
  "confidence": 0.95
}
```

### Extracting Service Lines with CPT Codes

```bash
curl -s -X POST -F "file=@form.pdf" http://localhost:8080/api/v1/forms/extract | \
  jq '.serviceLines[] | {lineNum: .lineNumber, cptCode: .cptCode, description: .cptDescription, amount: .chargedAmount}'
```

Output:
```json
{
  "lineNum": 1,
  "cptCode": "99213",
  "description": "Office visit, established patient",
  "amount": 150
}
```

## Error Handling Examples

### Check if Form is Valid

```bash
curl -s -X POST -F "file=@form.pdf" http://localhost:8080/api/v1/forms/extract | \
  jq 'if .formValid then "✓ Valid form" else "✗ Invalid or unrecognized form" end'
```

### List All Issues/Notes

```bash
curl -s -X POST -F "file=@form.pdf" http://localhost:8080/api/v1/forms/extract | \
  jq '.notes[] | {field: .fieldName, issue: .issue, confidence: .confidenceScore}'
```

### Filter Low Confidence Fields

```bash
curl -s -X POST -F "file=@form.pdf" http://localhost:8080/api/v1/forms/extract | \
  jq '.notes[] | select(.confidenceScore < 0.75) | {field: .fieldName, issue: .issue}'
```

## Advanced Usage

### Monitoring Processing Time

```bash
time curl -X POST -F "file=@form.pdf" http://localhost:8080/api/v1/forms/extract | \
  jq '{processingTimeMs: .processingTimeMs, formType: .formType, confidence: .confidenceScore}'
```

### Extracting Data to CSV (Multiple Files)

```bash
#!/bin/bash

echo "FileName,FormType,Valid,Confidence,PatientName,ProviderName" > results.csv

for file in forms/*.pdf; do
    result=$(curl -s -X POST -F "file=@$file" http://localhost:8080/api/v1/forms/extract)
    
    filename=$(basename "$file")
    formtype=$(echo "$result" | jq -r '.formType')
    valid=$(echo "$result" | jq -r '.formValid')
    confidence=$(echo "$result" | jq -r '.confidenceScore')
    patient=$(echo "$result" | jq -r '.patientDetails.firstName + " " + .patientDetails.lastName')
    provider=$(echo "$result" | jq -r '.providerDetails.providerName')
    
    echo "$filename,$formtype,$valid,$confidence,$patient,$provider" >> results.csv
done
```

### Real-time Monitoring with jq

```bash
# Monitor extraction and display key fields in real-time
curl -s -X POST -F "file=@form.pdf" http://localhost:8080/api/v1/forms/extract | \
  jq '{
    formType: .formType,
    valid: .formValid,
    confidence: (.confidenceScore * 100 | round) as $conf | "\($conf)%",
    patient: (.patientDetails.firstName + " " + .patientDetails.lastName),
    provider: .providerDetails.providerName,
    diagnosesCount: (.diagnosisCodes | length),
    serviceLinesCount: (.serviceLines | length),
    processingTime: (.processingTimeMs | tostring + "ms"),
    issuesCount: (.notes | length)
  }'
```

## Troubleshooting

### Issue: 400 Bad Request
**Cause**: Invalid file format or empty file
**Solution**: 
- Verify file is PDF or image (PNG, JPG, BMP, GIF)
- Check file size (max 100MB)
- Ensure file is not corrupted

```bash
# Verify file
file form.pdf
ls -lh form.pdf
```

### Issue: 500 Internal Server Error
**Cause**: Ollama not running or model not available
**Solution**:
```bash
# Check Ollama status
curl http://localhost:11434/api/tags

# If not running
ollama serve

# In another terminal, pull model if needed
ollama pull qwen2.5-vl-instruct
```

### Issue: Long Processing Times
**Cause**: Complex form or slow Ollama response
**Solution**:
- Try higher quality image/PDF
- Reduce form complexity if possible
- Ensure Ollama has sufficient system resources
- Check network latency if Ollama is remote

### Issue: Low Confidence Scores
**Cause**: Poor image quality, skewed form, overlapping fields
**Solution**:
- Capture clearer image with better lighting
- Ensure form is straight and aligned
- Avoid shadows or glare
- Use PDF instead of photographed copy if available

## Performance Optimization Tips

1. **Batch Processing**
   - Use `/extract-batch` for multiple forms
   - More efficient than individual requests

2. **Image Preparation**
   - Pre-process images for clarity
   - Ensure proper contrast
   - Remove backgrounds if possible

3. **Caching**
   - Cache API responses for repeat submissions
   - Store extracted data in database

4. **Monitoring**
   - Track confidence scores over time
   - Identify common low-confidence patterns
   - Use for model fine-tuning

## Integration Patterns

### With Database (PostgreSQL Example)

```python
from sqlalchemy import create_engine, Column, String, Float, DateTime, JSON
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
import requests
import json
from datetime import datetime

Base = declarative_base()

class FormExtraction(Base):
    __tablename__ = 'form_extractions'
    id = Column(String, primary_key=True)
    filename = Column(String)
    form_type = Column(String)
    form_valid = Column(bool)
    confidence_score = Column(Float)
    extracted_data = Column(JSON)
    created_at = Column(DateTime, default=datetime.utcnow)

# Store extraction result
engine = create_engine('postgresql://user:password@localhost/cms_forms')
Session = sessionmaker(bind=engine)
session = Session()

# Extract form
with open('form.pdf', 'rb') as f:
    response = requests.post('http://localhost:8080/api/v1/forms/extract', 
                            files={'file': f})
    result = response.json()

# Store in database
extraction = FormExtraction(
    id=str(uuid.uuid4()),
    filename='form.pdf',
    form_type=result['formType'],
    form_valid=result['formValid'],
    confidence_score=result['confidenceScore'],
    extracted_data=result
)

session.add(extraction)
session.commit()
```

This covers the main usage patterns for the CMS Form Extractor API. For more information, refer to README_IMPLEMENTATION.md.
