package com.shasank.cms_form_extractor.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.shasank.cms_form_extractor.config.OllamaProperties;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

@Service
public class OllamaClient {
    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_RESPONSE_TIMEOUT_SECONDS = 300;
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_ERROR_BODY_LENGTH = 2000;

    private final OllamaProperties ollamaProperties;
    private final HttpClient httpClient;

    public OllamaClient(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;

        Timeout responseTimeout = resolveResponseTimeout(ollamaProperties.getTimeoutSeconds());
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS))
            .setConnectionRequestTimeout(Timeout.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS))
            .setResponseTimeout(responseTimeout)
            .build();

        this.httpClient = HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)
            .evictExpiredConnections()
            .build();

        logger.info(
            "Configured Ollama HTTP timeouts - connect: {}s, response: {}",
            DEFAULT_CONNECT_TIMEOUT_SECONDS,
            responseTimeout == Timeout.DISABLED ? "disabled" : responseTimeout.toString()
        );
    }

    /**
     * Call Ollama API with image and prompt
     */
    public String generateResponse(String base64Image, String prompt) throws IOException {
        String url = ollamaProperties.getBaseUrl() + "/api/generate";

        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long startNanos = System.nanoTime();
            try {
                String response = executeGenerateRequest(url, base64Image, prompt);
                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
                logger.info("Ollama response received for model: {} in {} ms (attempt {}/{})",
                    ollamaProperties.getModel(), elapsedMs, attempt, MAX_ATTEMPTS);
                return response;
            } catch (IOException e) {
                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
                lastFailure = e;
                if (!isRetryable(e) || attempt == MAX_ATTEMPTS) {
                    logger.error("Error calling Ollama API after {} ms (attempt {}/{})", elapsedMs, attempt, MAX_ATTEMPTS, e);
                    throw new IOException("Failed to call Ollama API: " + e.getMessage(), e);
                }

                logger.warn(
                    "Retrying Ollama API call after {} ms due to timeout (attempt {}/{})",
                    elapsedMs,
                    attempt,
                    MAX_ATTEMPTS
                );
                sleepBeforeRetry(attempt);
            }
        }

        throw new IOException("Failed to call Ollama API", lastFailure);
    }

    /**
     * Call Ollama API with text-only prompt to avoid expensive multimodal inference on every refinement step.
     */
    public String generateTextResponse(String prompt) throws IOException {
        String url = ollamaProperties.getBaseUrl() + "/api/generate";

        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long startNanos = System.nanoTime();
            try {
                String response = executeGenerateRequest(url, null, prompt);
                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
                logger.info("Ollama text response received for model: {} in {} ms (attempt {}/{})",
                    ollamaProperties.getModel(), elapsedMs, attempt, MAX_ATTEMPTS);
                return response;
            } catch (IOException e) {
                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
                lastFailure = e;
                if (!isRetryable(e) || attempt == MAX_ATTEMPTS) {
                    logger.error("Error calling Ollama text API after {} ms (attempt {}/{})", elapsedMs, attempt, MAX_ATTEMPTS, e);
                    throw new IOException("Failed to call Ollama API: " + e.getMessage(), e);
                }

                logger.warn(
                    "Retrying Ollama text API call after {} ms due to timeout (attempt {}/{})",
                    elapsedMs,
                    attempt,
                    MAX_ATTEMPTS
                );
                sleepBeforeRetry(attempt);
            }
        }

        throw new IOException("Failed to call Ollama API", lastFailure);
    }

    private String executeGenerateRequest(String url, String base64Image, String prompt) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", ollamaProperties.getModel());
        requestBody.addProperty("prompt", prompt);
        if (base64Image != null && !base64Image.isBlank()) {
            com.google.gson.JsonArray imagesArray = new com.google.gson.JsonArray();
            imagesArray.add(base64Image);
            requestBody.add("images", imagesArray);
        }
        requestBody.addProperty("stream", false);

        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setEntity(new org.apache.hc.core5.http.io.entity.StringEntity(
            requestBody.toString(),
            StandardCharsets.UTF_8
        ));

        StringBuilder response = new StringBuilder();
        httpClient.execute(httpPost, httpResponse -> {
            HttpEntity entity = httpResponse.getEntity();
            if (httpResponse.getCode() < 200 || httpResponse.getCode() >= 300) {
                String errorBody = entity == null ? "<empty>" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
                throw new IOException(
                    "Ollama returned non-success status: " + httpResponse.getCode() +
                        ", body: " + truncate(errorBody, MAX_ERROR_BODY_LENGTH)
                );
            }

            if (entity != null) {
                response.append(EntityUtils.toString(entity, StandardCharsets.UTF_8));
            }
            return null;
        });

        JsonObject responseObj = gson.fromJson(response.toString(), JsonObject.class);
        if (responseObj == null || !responseObj.has("response")) {
            throw new IOException("Ollama response does not contain 'response' field: " + truncate(response.toString(), MAX_ERROR_BODY_LENGTH));
        }
        return responseObj.get("response").getAsString();
    }

    private Timeout resolveResponseTimeout(Integer configuredTimeoutSeconds) {
        if (configuredTimeoutSeconds == null) {
            return Timeout.ofSeconds(DEFAULT_RESPONSE_TIMEOUT_SECONDS);
        }
        if (configuredTimeoutSeconds <= 0) {
            // 0 or negative disables read timeout for long-running local model inference.
            return Timeout.DISABLED;
        }
        return Timeout.ofSeconds(configuredTimeoutSeconds);
    }

    private boolean isRetryable(IOException exception) {
        return hasCause(exception, SocketTimeoutException.class);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> targetType) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (targetType.isInstance(cursor)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private void sleepBeforeRetry(int attempt) throws IOException {
        long delayMillis = 500L * attempt;
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to retry Ollama API call", e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    /**
     * Check if Ollama is running and model is available
     */
    public boolean healthCheck() {
        try {
            String url = ollamaProperties.getBaseUrl() + "/api/tags";
            HttpGet httpGet = new HttpGet(url);

            final boolean[] isHealthy = {false};
            httpClient.execute(httpGet, httpResponse -> {
                if (httpResponse.getCode() != 200) {
                    isHealthy[0] = false;
                    return null;
                }
                HttpEntity entity = httpResponse.getEntity();
                if (entity == null) {
                    isHealthy[0] = false;
                    return null;
                }

                String body = new String(entity.getContent().readAllBytes(), StandardCharsets.UTF_8);
                // Basic check that target model exists in tag list.
                isHealthy[0] = body.contains(ollamaProperties.getModel());
                return null;
            });

            logger.info("Ollama health check: {}", isHealthy[0]);
            return isHealthy[0];
        } catch (Exception e) {
            logger.error("Ollama health check failed", e);
            return false;
        }
    }
}
