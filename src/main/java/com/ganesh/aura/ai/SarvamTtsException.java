package com.ganesh.aura.ai;

public class SarvamTtsException extends RuntimeException {

    private final Code code;

    public SarvamTtsException(Code code) {
        super(code.safeMessage());
        this.code = code;
    }

    public SarvamTtsException(Code code, Throwable cause) {
        super(code.safeMessage(), cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        CONFIGURATION_MISSING("Speech service is not configured. Set SARVAM_API_KEY."),
        AUTHENTICATION_FAILED("Speech service authentication failed. Check SARVAM_API_KEY."),
        RATE_LIMIT("Speech service is busy. Please try again shortly."),
        TIMEOUT("Speech generation took too long. Please try again."),
        PROVIDER_ERROR("Speech service is temporarily unavailable."),
        INVALID_RESPONSE("Speech service returned invalid audio. Please try again.");

        private final String safeMessage;

        Code(String safeMessage) { this.safeMessage = safeMessage; }
        public String safeMessage() { return safeMessage; }
    }
}
