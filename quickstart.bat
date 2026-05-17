@echo off
REM CMS Form Extractor - Quick Start for Windows

echo ======================================
echo CMS Form Extractor - Quick Start (Windows)
echo ======================================
echo.

REM Check if Maven is available
mvn -version >nul 2>&1
if errorlevel 1 (
    echo x Maven not found. Please install Maven and add to PATH
    exit /b 1
)

echo [OK] Maven is installed
echo.

REM Check if Java is available
java -version >nul 2>&1
if errorlevel 1 (
    echo x Java not found. Please install Java 25+ and add to PATH
    exit /b 1
)

echo [OK] Java is installed
echo.

echo Building CMS Form Extractor...
mvn clean package -DskipTests
if errorlevel 1 (
    echo x Build failed
    exit /b 1
)

echo [OK] Build successful
echo.

echo ======================================
echo Starting Ollama (if not running)
echo ======================================
echo.
echo Make sure Ollama is running locally!
echo If not started yet, run: ollama serve
echo Then in another terminal: ollama pull qwen2.5-vl-instruct
echo.

echo ======================================
echo Starting CMS Form Extractor API
echo ======================================
echo.
echo The API will be available at: http://localhost:8080
echo.
echo API Endpoints:
echo   POST   /api/v1/forms/extract          - Extract data from a single form
echo   POST   /api/v1/forms/extract-batch    - Extract data from multiple forms
echo   GET    /api/v1/forms/health           - Health check
echo   GET    /api/v1/forms/info             - API information
echo.
echo Example usage:
echo   curl -X POST -F "file=@form.pdf" http://localhost:8080/api/v1/forms/extract
echo.
echo.

REM Run application
java -jar target\cms-form-extractor-0.0.1-SNAPSHOT.jar

pause
