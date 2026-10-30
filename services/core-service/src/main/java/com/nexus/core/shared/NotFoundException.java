package com.nexus.core.shared;

/** Thrown when a requested record does not exist; reported to the client as HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
