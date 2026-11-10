package com.expensetracker.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Extends ResponseStatusException so the same handler also covers P2's exceptions. */
public class BadRequestException extends ResponseStatusException {
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
