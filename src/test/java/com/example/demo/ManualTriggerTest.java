package com.example.demo;

import com.example.demo.scheduler.AnalyticsScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class ManualTriggerTest {

    @Autowired
    private AnalyticsScheduler analyticsScheduler;

    @Test
    void triggerReport() {
        System.out.println(">>> Manually triggering report for verification...");
        analyticsScheduler.runImmediateForTesting();
        System.out.println(">>> Report triggered successfully!");
    }
}
