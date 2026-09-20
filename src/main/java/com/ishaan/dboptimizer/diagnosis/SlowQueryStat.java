package com.ishaan.dboptimizer.diagnosis;

public record SlowQueryStat(
        long queryId,
        String query,
        long calls,
        double meanExecTimeMs,
        double totalExecTimeMs,
        long rows
) {}