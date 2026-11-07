package com.vidacruz.agent.application.exception;

public class RagUnavailableException extends RuntimeException {

    public RagUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public RagUnavailableException(String message) {
        super(message);
    }
}
