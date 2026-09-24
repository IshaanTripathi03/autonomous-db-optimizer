package com.ishaan.dboptimizer.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ishaan.dboptimizer.service.KnowledgeIngestionService;

@RestController
public class KnowledgeController {

    private final KnowledgeIngestionService knowledgeIngestionService;

    public KnowledgeController(KnowledgeIngestionService knowledgeIngestionService) {
        this.knowledgeIngestionService = knowledgeIngestionService;
    }

    @GetMapping("/api/knowledge/files")
    public List<String> listFiles() throws IOException {
        return knowledgeIngestionService.listDocFiles();
    }

    @PostMapping("/api/knowledge/ingest")
    public Map<String, Object> ingest() throws IOException {
        int count = knowledgeIngestionService.ingestAllDocs();
        return Map.of("status", "success", "documentsIngested", count);
    }
}