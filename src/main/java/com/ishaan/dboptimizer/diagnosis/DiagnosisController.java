package com.ishaan.dboptimizer.diagnosis;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnosis")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    public DiagnosisController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    @GetMapping("/slow-queries")
    public List<DiagnosedQuery> getSlowQueries(@RequestParam(defaultValue = "10") int limit) {
        return diagnosisService.diagnoseSlowQueries(limit);
    }
}