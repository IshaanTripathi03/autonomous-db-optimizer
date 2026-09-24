package com.ishaan.dboptimizer.execution;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class IndexExecutionService {

    private static final Set<String> ALLOWED_INDEX_TYPES = Set.of("btree", "hnsw", "ivfflat", "gin");
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private final JdbcClient targetJdbcClient;

    public IndexExecutionService(JdbcClient targetJdbcClient) {
        this.targetJdbcClient = targetJdbcClient;
    }

    public IndexCreationResult createIndexConcurrently(IndexCreationRequest request) {
        String validationError = validate(request);
        if (validationError != null) {
            return new IndexCreationResult(false, null, validationError);
        }

        String indexName = request.indexName() != null
                ? request.indexName()
                : "idx_" + request.tableName() + "_" + String.join("_", request.columns());

        String columnList = String.join(", ", request.columns());

        String ddl = "CREATE INDEX CONCURRENTLY " + indexName
                + " ON " + request.tableName()
                + " USING " + request.indexType()
                + " (" + columnList + ")";

        try {
            targetJdbcClient.sql(ddl).update();
            return new IndexCreationResult(true, indexName, "Index created successfully");
        } catch (Exception e) {
            return new IndexCreationResult(false, indexName, "Execution failed: " + e.getMessage());
        }
    }

    private String validate(IndexCreationRequest request) {
        if (request.tableName() == null || !SAFE_IDENTIFIER.matcher(request.tableName()).matches()) {
            return "Invalid table name format";
        }

        if (request.columns() == null || request.columns().isEmpty()) {
            return "At least one column is required";
        }

        for (String column : request.columns()) {
            if (!SAFE_IDENTIFIER.matcher(column).matches()) {
                return "Invalid column name format: " + column;
            }
        }

        if (request.indexType() == null || !ALLOWED_INDEX_TYPES.contains(request.indexType().toLowerCase())) {
            return "Index type not allowed: " + request.indexType();
        }

        if (!tableExists(request.tableName())) {
            return "Table does not exist: " + request.tableName();
        }

        List<String> existingColumns = getColumnNames(request.tableName());
        for (String column : request.columns()) {
            if (!existingColumns.contains(column)) {
                return "Column does not exist on table " + request.tableName() + ": " + column;
            }
        }

        return null;
    }

    private boolean tableExists(String tableName) {
        Integer count = targetJdbcClient.sql(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = :tableName"
        ).param("tableName", tableName).query(Integer.class).single();
        return count != null && count > 0;
    }

    private List<String> getColumnNames(String tableName) {
        return targetJdbcClient.sql(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = :tableName"
        ).param("tableName", tableName).query(String.class).list();
    }
}
