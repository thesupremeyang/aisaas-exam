package com.hxh.apboa.assessment.service;

public class RuntimeAssessmentException extends RuntimeException {
    public enum Kind { TIMEOUT, CONNECTION, HTTP_STATUS, INVALID_RESPONSE }

    private final Kind kind;

    public RuntimeAssessmentException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public RuntimeAssessmentException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
