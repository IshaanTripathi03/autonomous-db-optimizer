package com.ishaan.dboptimizer.diagnosis;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class DiagnosisService {

    private final StatsCollectorService statsCollectorService;
    private final TableStatsService tableStatsService;

    public DiagnosisService(StatsCollectorService statsCollectorService, TableStatsService tableStatsService) {
        this.statsCollectorService = statsCollectorService;
        this.tableStatsService = tableStatsService;
    }

    public List<DiagnosedQuery> diagnoseSlowQueries(int limit) {
        return statsCollectorService.findSlowQueries(limit).stream()
                .map(stat -> {
                    String tableName = TableNameExtractor.extract(stat.query());
                    TableStat tableStat = tableName != null
                            ? tableStatsService.getTableStats(tableName)
                            : new TableStat(null, 0, List.of());
                    return new DiagnosedQuery(stat, tableStat);
                })
                .toList();
    }
}