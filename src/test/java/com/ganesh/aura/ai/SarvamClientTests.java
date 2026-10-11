package com.ganesh.aura.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static com.ganesh.aura.ai.SarvamTtsException.Code;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SarvamClientTests {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final byte[] WAV = new byte[] {
            'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '
    };

    @Test
    void sendsDocumentedRequestAndDecodesFirstAudio() throws Exception {
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        SarvamClient client = new SarvamClient(mapper, request -> {
            captured.set(request);
            return response(200, "{\"audios\":[\"" + Base64.getEncoder().encodeToString(WAV) + "\",\"ignored\"]}");
        }, "test-subscription-key", Duration.ofSeconds(12));

        assertThat(client.synthesize("Exact reply from chat.")).containsExactly(WAV);
        HttpRequest request = captured.get();
        assertThat(request.uri().toString()).isEqualTo("https://api.sarvam.ai/text-to-speech");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("api-subscription-key")).contains("test-subscription-key");
        assertThat(request.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(request.timeout()).contains(Duration.ofSeconds(12));
        var body = mapper.readTree(bodyAsString(request));
        assertThat(body.path("text").asText()).isEqualTo("Exact reply from chat.");
        assertThat(body.path("language_code").asText()).isEqualTo("en-IN");
        assertThat(body.has("target_language_code")).isFalse();
        assertThat(body.path("model").asText()).isEqualTo("bulbul:v4-flash");
        assertThat(body.path("speaker").asText()).isEqualTo("simran_en_customer");
        assertThat(body.path("output_audio_codec").asText()).isEqualTo("wav");
    }

    @Test
    void rejectsBlankAndOverLimitTextBeforeTransport() throws Exception {
        SarvamHttpTransport transport = mock(SarvamHttpTransport.class);
        SarvamClient client = new SarvamClient(mapper, transport, "test-key", Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.synthesize(" \n ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.synthesize("a".repeat(2_501))).isInstanceOf(IllegalArgumentException.class);
        verify(transport, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingApiKeyFailsWithoutTransportRequest() throws Exception {
        SarvamHttpTransport transport = mock(SarvamHttpTransport.class);
        SarvamClient client = new SarvamClient(mapper, transport, " ", Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.synthesize("Hello"))
                .isInstanceOf(SarvamTtsException.class)
                .extracting(error -> ((SarvamTtsException) error).getCode())
                .isEqualTo(Code.CONFIGURATION_MISSING);
        verify(transport, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsEmptyMissingMalformedAndNonWavAudioResponses() {
        assertInvalidResponse("");
        assertInvalidResponse("not json");
        assertInvalidResponse("{\"audios\":[]}");
        assertInvalidResponse("{\"audios\":[\"\"]}");
        assertInvalidResponse("{\"audios\":[\"not-base64!!!\"]}");
        assertInvalidResponse("{\"audios\":[\"" + Base64.getEncoder().encodeToString("not wav".getBytes()) + "\"]}");
    }

    @Test
    void mapsAuthenticationRateLimitProviderAndTimeoutFailures() throws Exception {
        assertStatus(401, Code.AUTHENTICATION_FAILED);
        assertStatus(403, Code.AUTHENTICATION_FAILED);
        assertStatus(429, Code.RATE_LIMIT);
        assertStatus(503, Code.PROVIDER_ERROR);
        assertStatus(408, Code.TIMEOUT);
        SarvamClient timeout = new SarvamClient(mapper, request -> { throw new java.net.http.HttpTimeoutException("private details"); },
                "test-key", Duration.ofSeconds(1));
        assertCode(timeout, Code.TIMEOUT);
    }

    private void assertInvalidResponse(String body) {
        SarvamClient client = new SarvamClient(mapper, request -> response(200, body), "test-key", Duration.ofSeconds(1));
        assertCode(client, Code.INVALID_RESPONSE);
    }

    private void assertStatus(int status, Code expected) {
        SarvamClient client = new SarvamClient(mapper, request -> response(status, "provider response must not be surfaced"),
                "test-key", Duration.ofSeconds(1));
        assertCode(client, expected);
    }

    private void assertCode(SarvamClient client, Code expected) {
        assertThatThrownBy(() -> client.synthesize("Hello"))
                .isInstanceOf(SarvamTtsException.class)
                .extracting(error -> ((SarvamTtsException) error).getCode())
                .isEqualTo(expected);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static String bodyAsString(HttpRequest request) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()]; item.get(bytes); output.writeBytes(bytes);
            }
            @Override public void onError(Throwable throwable) { throw new AssertionError(throwable); }
            @Override public void onComplete() { }
        });
        return output.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
