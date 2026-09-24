package com.ishaan.dboptimizer.execution;

import java.util.List;

public record IndexCreationRequest(
        String tableName,
        List<String> columns,
        String indexType,
        String indexName
) {}
