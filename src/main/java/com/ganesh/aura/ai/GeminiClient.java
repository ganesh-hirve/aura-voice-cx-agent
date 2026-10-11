package com.ganesh.aura.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

import static com.ganesh.aura.ai.GeminiClientException.Code;

/** Direct REST client for Gemini generateContent and native function calling. */
@Component
public class GeminiClient implements GeminiGateway {

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Pattern MODEL_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");

    private final ObjectMapper objectMapper;
    private final GeminiHttpTransport transport;
    private final String apiKey;
    private final String model;
    private final Duration readTimeout;

    public GeminiClient(
            ObjectMapper objectMapper,
            GeminiHttpTransport transport,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.model:gemini-3.5-flash-lite}") String model,
            @Value("${gemini.api.read-timeout:30s}") Duration readTimeout) {
        this.objectMapper = objectMapper;
        this.transport = transport;
        this.apiKey = apiKey;
        this.model = model;
        this.readTimeout = readTimeout;
    }

    @Override
    public JsonNode generateContent(String systemInstruction, ArrayNode contents, ArrayNode tools) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeminiClientException(Code.CONFIGURATION_MISSING);
        }
        if (model == null || !MODEL_PATTERN.matcher(model).matches()) {
            throw new GeminiClientException(Code.CONFIGURATION_MISSING);
        }

        HttpRequest request = buildRequest(systemInstruction, contents, tools);
        final HttpResponse<String> response;
        try {
            response = transport.send(request);
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw new GeminiClientException(Code.TIMEOUT, timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GeminiClientException(Code.TIMEOUT, interrupted);
        } catch (IOException transportFailure) {
            throw new GeminiClientException(Code.PROVIDER_ERROR, transportFailure);
        }

        int status = response.statusCode();
        if (status == 429) {
            throw new GeminiClientException(Code.RATE_LIMIT);
        }
        if (status == 408 || status == 504) {
            throw new GeminiClientException(Code.TIMEOUT);
        }
        if (status < 200 || status >= 300) {
            throw new GeminiClientException(Code.PROVIDER_ERROR);
        }
        if (response.body() == null || response.body().isBlank()) {
            throw new GeminiClientException(Code.INVALID_RESPONSE);
        }
        try {
            JsonNode parsed = objectMapper.readTree(response.body());
            if (parsed == null || !parsed.isObject()) {
                throw new GeminiClientException(Code.INVALID_RESPONSE);
            }
            return parsed;
        } catch (IOException invalidJson) {
            throw new GeminiClientException(Code.INVALID_RESPONSE, invalidJson);
        }
    }

    private HttpRequest buildRequest(String systemInstruction, ArrayNode contents, ArrayNode tools) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", systemInstruction);
        body.set("contents", contents.deepCopy());
        body.set("tools", tools.deepCopy());
        body.putObject("toolConfig").putObject("functionCallingConfig").put("mode", "AUTO");
        body.putObject("generationConfig").put("temperature", 0.2).put("maxOutputTokens", 512);

        try {
            String serializedBody = objectMapper.writeValueAsString(body);
            return HttpRequest.newBuilder(URI.create(API_URL + model + ":generateContent"))
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(serializedBody))
                    .build();
        } catch (IOException | IllegalArgumentException invalidRequest) {
            throw new GeminiClientException(Code.INVALID_RESPONSE, invalidRequest);
        }
    }
}
