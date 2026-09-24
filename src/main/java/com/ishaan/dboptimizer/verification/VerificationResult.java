package com.ishaan.dboptimizer.verification;

public record VerificationResult(
        String query,
        double executionTimeBeforeMs,
        double executionTimeAfterMs,
        double percentImprovement,
        String planBefore,
        String planAfter
) {}
