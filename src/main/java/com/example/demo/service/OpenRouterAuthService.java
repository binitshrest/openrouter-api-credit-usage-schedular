package com.example.demo.service;

import com.example.demo.config.OpenRouterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenRouterAuthService {

    private final OpenRouterProperties properties;

    public String getManagementKey() {
        String key = properties.getManagementKey();
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException(
                    "OpenRouter management key is missing. Please provide it in OPENROUTER_MANAGEMENT_KEY environment variable.");
        }
        return key;
    }
}
