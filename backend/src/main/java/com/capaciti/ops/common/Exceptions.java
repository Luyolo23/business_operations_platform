package com.capaciti.ops.common;

public final class Exceptions {
    private Exceptions() {}

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) { super(message); }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) { super(message); }
    }

    /** The request is valid but conflicts with the resource's current state (HTTP 409). */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }
}
