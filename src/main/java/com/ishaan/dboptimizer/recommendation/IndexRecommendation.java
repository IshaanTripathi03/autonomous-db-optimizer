package com.ishaan.dboptimizer.recommendation;

import java.util.List;

public record IndexRecommendation(
        String tableName,
        String indexType,
        List<String> columns,
        String indexName,
        String justification
) {}
