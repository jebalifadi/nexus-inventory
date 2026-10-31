package com.nexus.core.shared;

/** Thrown when a login fails; reported to the client as HTTP 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
