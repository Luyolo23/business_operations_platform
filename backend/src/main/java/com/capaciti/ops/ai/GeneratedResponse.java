package com.capaciti.ops.ai;

/** A drafted reply plus the provenance info we store for transparency. */
public record GeneratedResponse(String body, String generatedBy, String transparencyNote) {
}
