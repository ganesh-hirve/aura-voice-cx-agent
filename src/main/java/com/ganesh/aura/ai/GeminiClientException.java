package com.ganesh.aura.ai;

public class GeminiClientException extends RuntimeException {

    private final Code code;

    public GeminiClientException(Code code) {
        super(code.safeMessage());
        this.code = code;
    }

    public GeminiClientException(Code code, Throwable cause) {
        super(code.safeMessage(), cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        CONFIGURATION_MISSING("The AI service is not configured."),
        TIMEOUT("The AI service took too long to respond. Please try again."),
        RATE_LIMIT("The AI service is busy. Please try again shortly."),
        PROVIDER_ERROR("The AI service is temporarily unavailable."),
        INVALID_RESPONSE("The AI service returned an unreadable response.");

        private final String safeMessage;

        Code(String safeMessage) {
            this.safeMessage = safeMessage;
        }

        public String safeMessage() {
            return safeMessage;
        }
    }
}
