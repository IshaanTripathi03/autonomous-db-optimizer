package com.ishaan.dboptimizer.approval;

import java.time.Instant;

import com.ishaan.dboptimizer.recommendation.IndexRecommendation;
import com.ishaan.dboptimizer.verification.VerificationResult;

public class PendingApproval {

    private final String id;
    private final IndexRecommendation recommendation;
    private final Instant createdAt;
    private ApprovalStatus status;
    private String resultMessage;
    private VerificationResult verificationResult;

    public PendingApproval(String id, IndexRecommendation recommendation) {
        this.id = id;
        this.recommendation = recommendation;
        this.createdAt = Instant.now();
        this.status = ApprovalStatus.PENDING;
        this.resultMessage = null;
    }

    public String getId() {
        return id;
    }

    public IndexRecommendation getRecommendation() {
        return recommendation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public void setStatus(ApprovalStatus status) {
        this.status = status;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public VerificationResult getVerificationResult() {
        return verificationResult;
    }

    public void setVerificationResult(VerificationResult verificationResult) {
        this.verificationResult = verificationResult;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }
}
