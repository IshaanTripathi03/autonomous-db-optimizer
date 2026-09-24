package com.ishaan.dboptimizer.approval;

import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ishaan.dboptimizer.diagnosis.DiagnosedQuery;
import com.ishaan.dboptimizer.diagnosis.DiagnosisService;
import com.ishaan.dboptimizer.recommendation.IndexRecommendation;
import com.ishaan.dboptimizer.recommendation.RecommendationService;

@RestController
public class ApprovalController {

    private final DiagnosisService diagnosisService;
    private final RecommendationService recommendationService;
    private final ApprovalService approvalService;

    public ApprovalController(DiagnosisService diagnosisService,
                               RecommendationService recommendationService,
                               ApprovalService approvalService) {
        this.diagnosisService = diagnosisService;
        this.recommendationService = recommendationService;
        this.approvalService = approvalService;
    }

    @GetMapping("/api/recommendation")
    public ResponseEntity<PendingApproval> getRecommendation(@RequestParam(defaultValue = "1") int limit) {
        List<DiagnosedQuery> diagnosed = diagnosisService.diagnoseSlowQueries(limit);
        if (diagnosed.isEmpty()) {
            throw new IllegalStateException("No slow queries found to diagnose");
        }
        IndexRecommendation recommendation = recommendationService.recommend(diagnosed.get(0));
        PendingApproval approval = approvalService.queue(recommendation);
        if (approval == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.ok(approval);
    }

    @GetMapping("/api/approvals")
    public Collection<PendingApproval> listApprovals() {
        return approvalService.listAll();
    }

    @PostMapping("/api/approvals/{id}/approve")
    public PendingApproval approve(@PathVariable String id) {
        return approvalService.approve(id);
    }

    @PostMapping("/api/approvals/{id}/reject")
    public PendingApproval reject(@PathVariable String id) {
        return approvalService.reject(id);
    }
}
