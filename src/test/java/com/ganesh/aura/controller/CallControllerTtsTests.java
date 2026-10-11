package com.ganesh.aura.controller;

import com.ganesh.aura.ai.SarvamClient;
import com.ganesh.aura.ai.SarvamTtsException;
import com.ganesh.aura.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CallControllerTtsTests {

    private SarvamClient sarvamClient;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sarvamClient = mock(SarvamClient.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CallController(mock(ConversationService.class), sarvamClient)).build();
    }

    @Test
    void speakReturnsRawWavBytesWithAudioContentType() throws Exception {
        byte[] audio = new byte[] { 'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E' };
        when(sarvamClient.synthesize("Hello from Aria.")).thenReturn(audio);

        mockMvc.perform(post("/api/calls/speak")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Hello from Aria.\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"))
                .andExpect(content().bytes(audio));
    }

    @Test
    void rejectsBlankAndOverLimitTextBeforeCallingProvider() throws Exception {
        mockMvc.perform(post("/api/calls/speak").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));

        String tooLong = "a".repeat(2_501);
        mockMvc.perform(post("/api/calls/speak").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
        verify(sarvamClient, never()).synthesize(anyString());
    }

    @Test
    void mapsProviderFailuresToSafeHttpStatuses() {
        assertThat(new CallController(mock(ConversationService.class), sarvamClient)
                .handleSpeechFailure(new SarvamTtsException(SarvamTtsException.Code.CONFIGURATION_MISSING)).getStatusCode().value()).isEqualTo(503);
        assertThat(new CallController(mock(ConversationService.class), sarvamClient)
                .handleSpeechFailure(new SarvamTtsException(SarvamTtsException.Code.AUTHENTICATION_FAILED)).getStatusCode().value()).isEqualTo(502);
        assertThat(new CallController(mock(ConversationService.class), sarvamClient)
                .handleSpeechFailure(new SarvamTtsException(SarvamTtsException.Code.RATE_LIMIT)).getStatusCode().value()).isEqualTo(429);
        assertThat(new CallController(mock(ConversationService.class), sarvamClient)
                .handleSpeechFailure(new SarvamTtsException(SarvamTtsException.Code.TIMEOUT)).getStatusCode().value()).isEqualTo(504);
        assertThat(new CallController(mock(ConversationService.class), sarvamClient)
                .handleSpeechFailure(new SarvamTtsException(SarvamTtsException.Code.INVALID_RESPONSE)).getStatusCode().value()).isEqualTo(502);
    }

}
