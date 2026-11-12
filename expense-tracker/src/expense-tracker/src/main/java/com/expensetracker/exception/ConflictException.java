package com.expensetracker.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Extends ResponseStatusException so the same handler also covers P2's exceptions. */
public class ConflictException extends ResponseStatusException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
