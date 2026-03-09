package com.example.demo.controller;

import com.example.demo.scheduler.AnalyticsScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {

    private final AnalyticsScheduler analyticsScheduler;

    @PostMapping("/trigger-report")
    public ResponseEntity<String> triggerReport() {
        log.info("Received request to trigger report via API");
        try {
            analyticsScheduler.runImmediateForTesting();
            return ResponseEntity.ok("Report triggered successfully!");
        } catch (Exception e) {
            log.error("Failed to trigger report", e);
            return ResponseEntity.internalServerError().body("Failed to trigger report: " + e.getMessage());
        }
    }

    @PostMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
