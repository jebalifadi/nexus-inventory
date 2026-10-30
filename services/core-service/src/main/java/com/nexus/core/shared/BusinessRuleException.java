package com.nexus.core.shared;

/** Thrown when a request breaks a business rule; reported to the client as HTTP 400. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
