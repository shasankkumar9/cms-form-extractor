#!/bin/bash
# CMS Form Extractor - Quick Start Guide

echo "======================================"
echo "CMS Form Extractor - Quick Start"
echo "======================================"
echo ""

# Check if Ollama is installed
if ! command -v ollama &> /dev/null; then
    echo "❌ Ollama not found. Please install Ollama from https://ollama.ai"
    exit 1
fi

echo "✓ Ollama is installed"
echo ""

# Start Ollama
echo "Starting Ollama service..."
ollama serve &
OLLAMA_PID=$!
sleep 3

# Pull Qwen2.5-VL model if not already present
echo "Checking for Qwen2.5-VL model..."
if ! ollama list | grep -q "qwen2.5-vl-instruct"; then
    echo "Pulling Qwen2.5-VL model (this may take a few minutes)..."
    ollama pull qwen2.5-vl-instruct
else
    echo "✓ Qwen2.5-VL model already installed"
fi

echo ""
echo "Building Spring Boot application..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    kill $OLLAMA_PID
    exit 1
fi

echo "✓ Build successful"
echo ""
echo "======================================"
echo "Starting CMS Form Extractor API"
echo "======================================"
echo ""
echo "The API will be available at: http://localhost:8080"
echo ""
echo "API Endpoints:"
echo "  POST   /api/v1/forms/extract          - Extract data from a single form"
echo "  POST   /api/v1/forms/extract-batch    - Extract data from multiple forms"
echo "  GET    /api/v1/forms/health           - Health check"
echo "  GET    /api/v1/forms/info             - API information"
echo ""
echo "Example usage:"
echo "  curl -X POST -F \"file=@form.pdf\" http://localhost:8080/api/v1/forms/extract"
echo ""

# Run application
java -jar target/cms-form-extractor-0.0.1-SNAPSHOT.jar

# Cleanup
kill $OLLAMA_PID
