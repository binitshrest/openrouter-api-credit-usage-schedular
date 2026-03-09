package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenRouterAnalyticsService {

    private final OpenRouterAuthService authService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public AnalyticsData fetchAnalytics() {
        String managementKey = authService.getManagementKey();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(managementKey);
        HttpEntity<String> entity = new HttpEntity<>("", headers);

        AnalyticsData data = new AnalyticsData();

        // 1. Fetch Credits Balance (Real-time lifetime usage)
        fetchCredits(data, entity);

        // 2. Fetch Today's Usage per Key (Real-time today usage)
        fetchTodayUsage(data, entity);

        // 3. Fetch Historical Activity (Last 30 completed days)
        fetchActivityParallel(data, entity);

        // 4. Calculate Unclassified Usage (Delta between total spent and breakdown sum)
        double breakdownTotal = data.getModels().stream().mapToDouble(ModelStat::getCost).sum();
        data.setUnclassifiedUsage(Math.max(0, data.getTotalSpent() - breakdownTotal));

        return data;
    }

    private void fetchTodayUsage(AnalyticsData data, HttpEntity<String> entity) {
        String url = "https://openrouter.ai/api/v1/keys";
        log.info("Fetching daily usage per key from {}", url);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode body = objectMapper.readTree(response.getBody());
            double todayTotal = 0.0;
            if (body != null && body.has("data")) {
                JsonNode keysArray = body.get("data");
                if (keysArray.isArray()) {
                    for (JsonNode keyNode : keysArray) {
                        if (keyNode.has("usage_daily")) {
                            todayTotal += keyNode.get("usage_daily").asDouble();
                        }
                    }
                }
            }
            data.setTodaySpent(todayTotal);
            log.info("Real-time today usage sum: {}", todayTotal);
        } catch (Exception e) {
            log.error("Failed to fetch daily usage per key", e);
        }
    }

    private void fetchCredits(AnalyticsData data, HttpEntity<String> entity) {
        String url = "https://openrouter.ai/api/v1/credits";
        log.info("Fetching credits from {}", url);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode body = objectMapper.readTree(response.getBody());
            log.info("Credits API Response: {}", body);
            if (body != null && body.has("data")) {
                JsonNode creditsNode = body.get("data");
                if (creditsNode.has("total_usage")) {
                    data.setTotalSpent(creditsNode.get("total_usage").asDouble());
                }
                if (creditsNode.has("limit")) {
                    data.setTotalCredits(creditsNode.get("limit").asDouble());
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch credits from official API", e);
        }
    }

    private void fetchActivityParallel(AnalyticsData data, HttpEntity<String> entity) {
        java.time.LocalDate todayUtc = java.time.LocalDate.now(java.time.ZoneId.of("UTC"));
        List<String> datesToFetch = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            datesToFetch.add(todayUtc.minusDays(i).toString());
        }

        java.util.Map<String, ModelStat> aggregatedStats = new java.util.concurrent.ConcurrentHashMap<>();
        List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

        for (String dateStr : datesToFetch) {
            futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                String url = "https://openrouter.ai/api/v1/activity?date=" + dateStr;
                try {
                    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                    JsonNode body = objectMapper.readTree(response.getBody());

                    if (body != null && body.has("data")) {
                        JsonNode dataArray = body.get("data");
                        if (dataArray.isArray()) {
                            for (JsonNode item : dataArray) {
                                String modelName = item.has("model") ? item.get("model").asText() : "Unknown";
                                ModelStat stat = aggregatedStats.computeIfAbsent(modelName, k -> {
                                    ModelStat newStat = new ModelStat();
                                    newStat.setModelName(k);
                                    return newStat;
                                });

                                double usage = item.has("usage") ? item.get("usage").asDouble() : 0.0;
                                double byokUsage = item.has("byok_usage_inference")
                                        ? item.get("byok_usage_inference").asDouble()
                                        : 0.0;
                                int requests = item.has("requests") ? item.get("requests").asInt() : 0;
                                int byokRequests = item.has("byok_requests") ? item.get("byok_requests").asInt() : 0;
                                long inTokens = item.has("prompt_tokens") ? item.get("prompt_tokens").asLong() : 0L;
                                long outTokens = item.has("completion_tokens") ? item.get("completion_tokens").asLong()
                                        : 0L;

                                synchronized (stat) {
                                    stat.setCost(stat.getCost() + usage + byokUsage);
                                    stat.setRequests(stat.getRequests() + requests + byokRequests);
                                    stat.setInTokens(stat.getInTokens() + inTokens);
                                    stat.setOutTokens(stat.getOutTokens() + outTokens);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to fetch activity for {} from official API", dateStr, e);
                }
            }));
        }

        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                .join();
        data.setModels(new ArrayList<>(aggregatedStats.values()));
        log.info("Aggregated {} unique models across last 30 completed days", aggregatedStats.size());
    }

    @lombok.Data
    public static class AnalyticsData {
        private double totalCredits = 10.0;
        private double totalSpent = 0.0; // Lifetime consumed
        private double todaySpent = 0.0; // Real-time today
        private double unclassifiedUsage = 0.0; // Usage not yet in breakdown
        private List<ModelStat> models = new ArrayList<>();
    }

    @lombok.Data
    public static class ModelStat {
        private String modelName;
        private double cost;
        private long inTokens;
        private long outTokens;
        private int requests;
    }
}
