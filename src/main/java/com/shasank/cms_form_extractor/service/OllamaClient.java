package com.shasank.cms_form_extractor.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.shasank.cms_form_extractor.config.OllamaProperties;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class OllamaClient {
    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final OllamaProperties ollamaProperties;
    private final HttpClient httpClient;

    public OllamaClient(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Call Ollama API with image and prompt
     */
    public String generateResponse(String base64Image, String prompt) throws IOException {
        String url = ollamaProperties.getBaseUrl() + "/api/generate";

        // Build images array
        com.google.gson.JsonArray imagesArray = new com.google.gson.JsonArray();
        imagesArray.add(base64Image);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", ollamaProperties.getModel());
        requestBody.addProperty("prompt", prompt);
        requestBody.add("images", imagesArray);
        requestBody.addProperty("stream", false);

        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setEntity(new org.apache.hc.core5.http.io.entity.StringEntity(
            requestBody.toString(),
            StandardCharsets.UTF_8
        ));

        try {
            StringBuilder response = new StringBuilder();
            httpClient.execute(httpPost, httpResponse -> {
                if (httpResponse.getCode() < 200 || httpResponse.getCode() >= 300) {
                    throw new IOException("Ollama returned non-success status: " + httpResponse.getCode());
                }
                HttpEntity entity = httpResponse.getEntity();
                if (entity != null) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(entity.getContent(), StandardCharsets.UTF_8)
                    );
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                return null;
            });

            logger.info("Ollama response received for model: {}", ollamaProperties.getModel());

            // Parse the JSON response
            JsonObject responseObj = gson.fromJson(response.toString(), JsonObject.class);
            if (responseObj == null || !responseObj.has("response")) {
                throw new IOException("Ollama response does not contain 'response' field: " + response);
            }
            return responseObj.get("response").getAsString();
        } catch (Exception e) {
            logger.error("Error calling Ollama API", e);
            throw new IOException("Failed to call Ollama API: " + e.getMessage(), e);
        }
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
