package com.ishaan.dboptimizer.verification;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class VerificationService {

    private static final Pattern EXEC_TIME_PATTERN = Pattern.compile("Execution Time: ([0-9.]+) ms");
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Set<String> TEXT_LIKE_TYPES = Set.of(
            "character varying", "character", "text", "date", "timestamp", "timestamp without time zone", "timestamp with time zone", "uuid"
    );

    private final JdbcClient targetJdbcClient;

    public VerificationService(JdbcClient targetJdbcClient) {
        this.targetJdbcClient = targetJdbcClient;
    }

    public String buildSampleQuery(String tableName, List<String> columns) {
        validateIdentifier(tableName);
        columns.forEach(this::validateIdentifier);

        StringBuilder sb = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE ");

        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            String literal = getSampleValueLiteral(tableName, column);

            if (i > 0) {
                sb.append(" AND ");
            }
            sb.append(column).append(" = ").append(literal);
        }

        return sb.toString();
    }

    private void validateIdentifier(String identifier) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid identifier for verification query: " + identifier);
        }
    }

    private String getSampleValueLiteral(String tableName, String column) {
        String dataType = targetJdbcClient.sql(
                "SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = :tableName AND column_name = :column"
        ).param("tableName", tableName).param("column", column).query(String.class).single();

        String rawValue = targetJdbcClient.sql(
                "SELECT " + column + "::text FROM " + tableName + " WHERE " + column + " IS NOT NULL LIMIT 1"
        ).query(String.class).single();

        boolean isTextLike = TEXT_LIKE_TYPES.contains(dataType.toLowerCase());
        return isTextLike ? "'" + rawValue.replace("'", "''") + "'" : rawValue;
    }

    public String runExplainAnalyze(String query) {
        List<String> planLines = targetJdbcClient.sql("EXPLAIN ANALYZE " + query)
                .query(String.class)
                .list();
        return String.join("\n", planLines);
    }

    public double extractExecutionTimeMs(String explainOutput) {
        Matcher matcher = EXEC_TIME_PATTERN.matcher(explainOutput);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return -1;
    }

    public VerificationResult verify(String query, String planBefore, double timeBefore) {
        String planAfter = runExplainAnalyze(query);
        double timeAfter = extractExecutionTimeMs(planAfter);

        double percentImprovement = timeBefore > 0
                ? ((timeBefore - timeAfter) / timeBefore) * 100.0
                : 0.0;

        return new VerificationResult(query, timeBefore, timeAfter, percentImprovement, planBefore, planAfter);
    }
}
