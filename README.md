# CMS Form Extractor

Spring Boot API that accepts images or PDFs of CMS claim forms and extracts structured JSON using Ollama + `qwen2.5-vl-instruct`.

## What It Does

- Accepts `CMS-1500` / `UB-04` form uploads.
- Validates whether the uploaded document is a supported CMS form.
- Extracts patient/provider/physician details, diagnosis codes, CPT service lines, dates, and claim metadata.
- Uses multi-pass refinement (agentic loop) plus ambiguity notes for low-confidence fields.

## Tech Stack

- Java 25
- Spring Boot 4.0
- Apache PDFBox (PDF to image conversion)
- Ollama HTTP API (`/api/generate`) with vision model input

## Project Structure

- `src/main/java/com/shasank/cms_form_extractor/controller` - REST endpoints
- `src/main/java/com/shasank/cms_form_extractor/service` - extraction workflow, prompts, Ollama client
- `src/main/java/com/shasank/cms_form_extractor/dto` - response DTOs
- `src/main/resources/application.yaml` - app/model config

## Quick Start

1. Start Ollama and pull the model:

```bash
ollama serve
ollama pull qwen2.5-vl-instruct
```

2. Build and run the API:

```bash
./mvnw clean test
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

## API Endpoints

- `POST /api/v1/forms/extract` (`multipart/form-data`, field: `file`)
- `POST /api/v1/forms/extract-batch` (`multipart/form-data`, field: `files`)
- `GET /api/v1/forms/health`
- `GET /api/v1/forms/info`

## Test the API

PowerShell example:

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/forms/extract" -F "file=@C:\path\to\form.pdf"
```

## Configuration

Default values are in `src/main/resources/application.yaml`:

- `ollama.base-url` (default `http://localhost:11434`)
- `ollama.model` (default `qwen2.5-vl-instruct`)
- `form.extraction.max-retries`
- `form.extraction.confidence-threshold`

## Docker

A starter `docker-compose.yml` is included with both services:

```bash
docker compose up --build
```

Note: First model download can take several minutes depending on network.
