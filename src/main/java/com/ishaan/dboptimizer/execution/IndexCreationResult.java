package com.ishaan.dboptimizer.execution;

public record IndexCreationResult(
        boolean success,
        String indexName,
        String message
) {}
