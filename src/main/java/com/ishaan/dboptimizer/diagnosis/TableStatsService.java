package com.ishaan.dboptimizer.diagnosis;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class TableStatsService {

    private final JdbcClient targetJdbcClient;

    public TableStatsService(JdbcClient targetJdbcClient) {
        this.targetJdbcClient = targetJdbcClient;
    }

    public TableStat getTableStats(String tableName) {
        Long rowCount = targetJdbcClient.sql("""
                        SELECT reltuples::bigint AS estimate
                        FROM pg_class
                        WHERE relname = :tableName
                        """)
                .param("tableName", tableName)
                .query(Long.class)
                .optional()
                .orElse(0L);

        List<String> indexNames = targetJdbcClient.sql("""
                        SELECT indexname
                        FROM pg_indexes
                        WHERE tablename = :tableName
                        """)
                .param("tableName", tableName)
                .query(String.class)
                .list();

        return new TableStat(tableName, rowCount, indexNames);
    }
}