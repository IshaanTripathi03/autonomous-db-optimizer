package com.ishaan.dboptimizer.diagnosis;

import java.util.List;

public record TableStat(
        String tableName,
        long estimatedRowCount,
        List<String> indexNames
) {}