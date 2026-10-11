package com.ganesh.aura.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/** Direct REST client for Sarvam Bulbul text-to-speech. */
@Component
public class SarvamClient {

    private static final URI API_URI = URI.create("https://api.sarvam.ai/text-to-speech");
    private static final int MAX_TEXT_CODE_POINTS = 2_500;

    private final ObjectMapper objectMapper;
    private final SarvamHttpTransport transport;
    private final String apiKey;
    private final Duration requestTimeout;

    public SarvamClient(
            ObjectMapper objectMapper,
            SarvamHttpTransport transport,
            @Value("${sarvam.api.key:}") String apiKey,
            @Value("${sarvam.api.read-timeout:30s}") Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.transport = transport;
        this.apiKey = apiKey;
        this.requestTimeout = requestTimeout;
    }

    public byte[] synthesize(String text) {
        validateText(text);
        if (apiKey == null || apiKey.isBlank()) {
            throw new SarvamTtsException(SarvamTtsException.Code.CONFIGURATION_MISSING);
        }

        HttpResponse<String> response;
        try {
            response = transport.send(buildRequest(text));
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw new SarvamTtsException(SarvamTtsException.Code.TIMEOUT, timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SarvamTtsException(SarvamTtsException.Code.TIMEOUT, interrupted);
        } catch (IOException transportFailure) {
            throw new SarvamTtsException(SarvamTtsException.Code.PROVIDER_ERROR, transportFailure);
        }

        int status = response.statusCode();
        if (status == 429) throw new SarvamTtsException(SarvamTtsException.Code.RATE_LIMIT);
        if (status == 401 || status == 403) throw new SarvamTtsException(SarvamTtsException.Code.AUTHENTICATION_FAILED);
        if (status == 408 || status == 504) throw new SarvamTtsException(SarvamTtsException.Code.TIMEOUT);
        if (status < 200 || status >= 300) throw new SarvamTtsException(SarvamTtsException.Code.PROVIDER_ERROR);

        return decodeAudio(response.body());
    }

    private static void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text must not be blank.");
        }
        if (text.codePointCount(0, text.length()) > MAX_TEXT_CODE_POINTS) {
            throw new IllegalArgumentException("Text must be 2500 characters or fewer.");
        }
    }

    private HttpRequest buildRequest(String text) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("text", text);
        body.put("language_code", "en-IN");
        body.put("model", "bulbul:v4-flash");
        body.put("speaker", "simran_en_customer");
        body.put("output_audio_codec", "wav");
        try {
            return HttpRequest.newBuilder(API_URI)
                    .timeout(requestTimeout)
                    .header("api-subscription-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (IOException | IllegalArgumentException invalidRequest) {
            throw new SarvamTtsException(SarvamTtsException.Code.CONFIGURATION_MISSING, invalidRequest);
        }
    }

    private byte[] decodeAudio(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new SarvamTtsException(SarvamTtsException.Code.INVALID_RESPONSE);
        }
        try {
            JsonNode parsed = objectMapper.readTree(responseBody);
            JsonNode audios = parsed == null ? null : parsed.path("audios");
            if (audios == null || !audios.isArray() || audios.isEmpty() || !audios.get(0).isTextual()) {
                throw new SarvamTtsException(SarvamTtsException.Code.INVALID_RESPONSE);
            }
            byte[] wav = Base64.getDecoder().decode(audios.get(0).textValue());
            if (!isWav(wav)) throw new SarvamTtsException(SarvamTtsException.Code.INVALID_RESPONSE);
            return wav;
        } catch (IOException | IllegalArgumentException malformedResponse) {
            throw new SarvamTtsException(SarvamTtsException.Code.INVALID_RESPONSE, malformedResponse);
        }
    }

    private static boolean isWav(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E';
    }
}
