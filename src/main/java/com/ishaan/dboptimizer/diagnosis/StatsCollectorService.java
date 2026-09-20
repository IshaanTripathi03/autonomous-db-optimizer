package com.ishaan.dboptimizer.diagnosis;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class StatsCollectorService {

    private final JdbcClient targetJdbcClient;

    public StatsCollectorService(JdbcClient targetJdbcClient) {
        this.targetJdbcClient = targetJdbcClient;
    }

    public List<SlowQueryStat> findSlowQueries(int limit) {
        String sql = """
                SELECT queryid, query, calls, mean_exec_time, total_exec_time, rows
                FROM pg_stat_statements
                WHERE query NOT ILIKE '%pg_stat_statements%'
                ORDER BY mean_exec_time DESC
                LIMIT :limit
                """;

        return targetJdbcClient.sql(sql)
                .param("limit", limit)
                .query((rs, rowNum) -> new SlowQueryStat(
                        rs.getLong("queryid"),
                        rs.getString("query"),
                        rs.getLong("calls"),
                        rs.getDouble("mean_exec_time"),
                        rs.getDouble("total_exec_time"),
                        rs.getLong("rows")
                ))
                .list();
    }
}