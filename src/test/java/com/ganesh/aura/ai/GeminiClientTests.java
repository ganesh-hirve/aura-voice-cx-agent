package com.ganesh.aura.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static com.ganesh.aura.ai.GeminiClientException.Code;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiClientTests {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendsNativeGenerateContentRequestWithKeyOnlyInHeader() throws Exception {
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"}]}}]}");
        GeminiClient client = new GeminiClient(mapper, request -> {
            assertThat(request.uri().toString()).contains("/v1beta/models/gemini-3.5-flash-lite:generateContent");
            assertThat(request.headers().firstValue("x-goog-api-key")).contains("test-key");
            assertThat(request.bodyPublisher()).isPresent();
            return response;
        }, "test-key", "gemini-3.5-flash-lite", Duration.ofSeconds(10));
        var result = client.generateContent("system", mapper.createArrayNode(), mapper.createArrayNode());
        assertThat(result.path("candidates")).isNotEmpty();
    }

    @Test
    void missingKeyFailsWithoutSendingRequest() throws Exception {
        GeminiHttpTransport transport = mock(GeminiHttpTransport.class);
        GeminiClient client = new GeminiClient(mapper, transport, " ", "gemini-3.5-flash-lite", Duration.ofSeconds(1));
        assertThatThrownBy(() -> client.generateContent("system", mapper.createArrayNode(), mapper.createArrayNode()))
                .isInstanceOf(GeminiClientException.class)
                .extracting(error -> ((GeminiClientException) error).getCode())
                .isEqualTo(Code.CONFIGURATION_MISSING);
        verify(transport, never()).send(any(HttpRequest.class));
    }

    @Test
    void mapsRateLimitProviderErrorAndInvalidJsonSafely() throws Exception {
        assertStatusMapsTo(429, Code.RATE_LIMIT, "{}");
        assertStatusMapsTo(503, Code.PROVIDER_ERROR, "provider internals and key must not surface");
        assertStatusMapsTo(200, Code.INVALID_RESPONSE, "not-json");
    }

    @Test
    void mapsTransportTimeoutSafely() {
        GeminiClient client = new GeminiClient(mapper, request -> {
            throw new java.net.http.HttpTimeoutException("private transport data");
        }, "test-key", "gemini-3.5-flash-lite", Duration.ofSeconds(1));
        assertThatThrownBy(() -> client.generateContent("system", mapper.createArrayNode(), mapper.createArrayNode()))
                .isInstanceOf(GeminiClientException.class)
                .extracting(error -> ((GeminiClientException) error).getCode())
                .isEqualTo(Code.TIMEOUT);
    }

    private void assertStatusMapsTo(int status, Code expected, String body) throws Exception {
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        GeminiClient client = new GeminiClient(mapper, request -> response,
                "test-key", "gemini-3.5-flash-lite", Duration.ofSeconds(1));
        assertThatThrownBy(() -> client.generateContent("system", mapper.createArrayNode(), mapper.createArrayNode()))
                .isInstanceOf(GeminiClientException.class)
                .extracting(error -> ((GeminiClientException) error).getCode())
                .isEqualTo(expected);
    }
}
