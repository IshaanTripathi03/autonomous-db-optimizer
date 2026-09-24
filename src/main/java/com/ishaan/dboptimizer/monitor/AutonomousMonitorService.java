package com.ishaan.dboptimizer.monitor;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ishaan.dboptimizer.approval.ApprovalService;
import com.ishaan.dboptimizer.approval.PendingApproval;
import com.ishaan.dboptimizer.diagnosis.DiagnosedQuery;
import com.ishaan.dboptimizer.diagnosis.DiagnosisService;
import com.ishaan.dboptimizer.recommendation.IndexRecommendation;
import com.ishaan.dboptimizer.recommendation.RecommendationService;

@Service
public class AutonomousMonitorService {

    private static final Logger log = LoggerFactory.getLogger(AutonomousMonitorService.class);

    private final DiagnosisService diagnosisService;
    private final RecommendationService recommendationService;
    private final ApprovalService approvalService;
    private volatile Instant cooldownUntil = Instant.MIN;
    private static final long COOLDOWN_SECONDS = 120;

    public AutonomousMonitorService(DiagnosisService diagnosisService,
                                     RecommendationService recommendationService,
                                     ApprovalService approvalService) {
        this.diagnosisService = diagnosisService;
        this.recommendationService = recommendationService;
        this.approvalService = approvalService;
    }

    @Scheduled(fixedRateString = "${app.monitor.interval-ms:300000}")
    public void watchAndDiagnose() {
        log.info("Autonomous monitor: scanning for slow queries...");

        List<DiagnosedQuery> diagnosed = diagnosisService.diagnoseSlowQueries(1);

        if (diagnosed.isEmpty()) {
            log.info("Autonomous monitor: no slow queries found this cycle.");
            return;
        }

        DiagnosedQuery topQuery = diagnosed.get(0);
        String tableName = topQuery.tableStat().tableName();
        log.info("Autonomous monitor: diagnosed slow query on table '{}', mean exec time {} ms",
                tableName, topQuery.queryStat().meanExecTimeMs());

        if (approvalService.hasActiveApprovalForTable(tableName)) {
            log.info("Autonomous monitor: active approval already exists for table={}, skipping Gemini call.", tableName);
            return;
        }

        if (Instant.now().isBefore(cooldownUntil)) {
            log.info("Autonomous monitor: in cooldown after a previous failure, skipping Gemini call until {}.", cooldownUntil);
            return;
        }

        IndexRecommendation recommendation;
        try {
            recommendation = recommendationService.recommend(topQuery);
        } catch (Exception e) {
            cooldownUntil = Instant.now().plusSeconds(COOLDOWN_SECONDS);
            log.warn("Autonomous monitor: Gemini call failed ({}), entering {}s cooldown.", e.getMessage(), COOLDOWN_SECONDS);
            return;
        }

        PendingApproval approval = approvalService.queue(recommendation);

        if (approval == null) {
            log.info("Autonomous monitor: recommendation for table={}, columns={} already pending or applied, skipping duplicate.",
                    recommendation.tableName(), recommendation.columns());
            return;
        }

        log.info("Autonomous monitor: queued recommendation [{}] for approval - table={}, columns={}",
                approval.getId(), recommendation.tableName(), recommendation.columns());
    }
}
