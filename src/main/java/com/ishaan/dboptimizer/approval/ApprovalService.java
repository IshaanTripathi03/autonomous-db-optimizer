package com.ishaan.dboptimizer.approval;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.ishaan.dboptimizer.execution.IndexCreationRequest;
import com.ishaan.dboptimizer.execution.IndexCreationResult;
import com.ishaan.dboptimizer.execution.IndexExecutionService;
import com.ishaan.dboptimizer.recommendation.IndexRecommendation;
import com.ishaan.dboptimizer.verification.VerificationResult;
import com.ishaan.dboptimizer.verification.VerificationService;

@Service
public class ApprovalService {

    private final Map<String, PendingApproval> approvals = new ConcurrentHashMap<>();
    private final IndexExecutionService indexExecutionService;
    private final VerificationService verificationService;

    public ApprovalService(IndexExecutionService indexExecutionService, VerificationService verificationService) {
        this.indexExecutionService = indexExecutionService;
        this.verificationService = verificationService;
    }

    public PendingApproval queue(IndexRecommendation recommendation) {
        boolean alreadyExists = approvals.values().stream().anyMatch(a ->
                a.getRecommendation().tableName().equals(recommendation.tableName())
                        && a.getRecommendation().columns().equals(recommendation.columns())
                        && (a.getStatus() == ApprovalStatus.PENDING || a.getStatus() == ApprovalStatus.APPLIED)
        );

        if (alreadyExists) {
            return null;
        }

        String id = UUID.randomUUID().toString();
        PendingApproval approval = new PendingApproval(id, recommendation);
        approvals.put(id, approval);
        return approval;
    }

    public boolean hasActiveApprovalForTable(String tableName) {
        return approvals.values().stream().anyMatch(a ->
                a.getRecommendation().tableName().equals(tableName)
                        && (a.getStatus() == ApprovalStatus.PENDING || a.getStatus() == ApprovalStatus.APPLIED)
        );
    }

    public Collection<PendingApproval> listAll() {
        return approvals.values();
    }

    public PendingApproval get(String id) {
        PendingApproval approval = approvals.get(id);
        if (approval == null) {
            throw new IllegalArgumentException("No approval found with id: " + id);
        }
        return approval;
    }

    public PendingApproval approve(String id) {
        PendingApproval approval = get(id);

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval " + id + " is not pending (current status: " + approval.getStatus() + ")");
        }

        approval.setStatus(ApprovalStatus.APPROVED);

        IndexRecommendation rec = approval.getRecommendation();

        String sampleQuery = verificationService.buildSampleQuery(rec.tableName(), rec.columns());
        String planBefore = verificationService.runExplainAnalyze(sampleQuery);
        double timeBefore = verificationService.extractExecutionTimeMs(planBefore);

        IndexCreationRequest request = new IndexCreationRequest(
                rec.tableName(),
                rec.columns(),
                rec.indexType(),
                rec.indexName()
        );

        IndexCreationResult result = indexExecutionService.createIndexConcurrently(request);

        approval.setStatus(result.success() ? ApprovalStatus.APPLIED : ApprovalStatus.FAILED);
        approval.setResultMessage(result.message());

        if (result.success()) {
            VerificationResult verification = verificationService.verify(sampleQuery, planBefore, timeBefore);
            approval.setVerificationResult(verification);
        }

        return approval;
    }

    public PendingApproval reject(String id) {
        PendingApproval approval = get(id);

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval " + id + " is not pending (current status: " + approval.getStatus() + ")");
        }

        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setResultMessage("Rejected by user");
        return approval;
    }
}
