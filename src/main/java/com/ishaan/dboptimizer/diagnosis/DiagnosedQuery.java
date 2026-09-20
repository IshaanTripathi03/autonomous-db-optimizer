package com.ishaan.dboptimizer.diagnosis;

public record DiagnosedQuery(
        SlowQueryStat queryStat,
        TableStat tableStat
) {}