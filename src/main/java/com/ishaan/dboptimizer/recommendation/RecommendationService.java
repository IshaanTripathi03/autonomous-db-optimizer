package com.ishaan.dboptimizer.recommendation;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.ishaan.dboptimizer.diagnosis.DiagnosedQuery;

@Service
public class RecommendationService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RecommendationService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    public IndexRecommendation recommend(DiagnosedQuery diagnosedQuery) {
        String searchText = buildSearchText(diagnosedQuery);

        List<Document> knowledgeChunks = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(searchText)
                        .topK(3)
                        .build()
        );

        String knowledgeContext = knowledgeChunks.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n---\n" + b);

        String prompt = buildPrompt(diagnosedQuery, knowledgeContext);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(IndexRecommendation.class);
    }

    private String buildSearchText(DiagnosedQuery dq) {
        return "Slow query on table " + dq.tableStat().tableName()
                + " with " + dq.tableStat().estimatedRowCount() + " rows, "
                + "existing indexes: " + dq.tableStat().indexNames()
                + ". Query: " + dq.queryStat().query();
    }

    private String buildPrompt(DiagnosedQuery dq, String knowledgeContext) {
        return """
                You are a PostgreSQL indexing expert. Analyze this slow query and recommend ONE index fix.

                SLOW QUERY DIAGNOSIS:
                - SQL: %s
                - Mean execution time: %.2f ms
                - Total calls: %d
                - Table: %s
                - Estimated row count: %d
                - Existing indexes: %s

                RELEVANT INDEXING KNOWLEDGE:
                %s

                Based on the diagnosis and the knowledge above, recommend exactly one index to create.
                Justify your choice by referencing the specific reasoning from the knowledge base,
                such as why IVFFlat vs HNSW applies, why this column order matters for a composite
                index, or why a partial index fits here. Do not suggest raw SQL execution, only the
                structured fields.
                """.formatted(
                dq.queryStat().query(),
                dq.queryStat().meanExecTimeMs(),
                dq.queryStat().calls(),
                dq.tableStat().tableName(),
                dq.tableStat().estimatedRowCount(),
                dq.tableStat().indexNames(),
                knowledgeContext
        );
    }
}
