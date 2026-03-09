package com.example.demo.service;

import com.example.demo.config.TelegramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class TelegramBotService {

    private final TelegramProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public TelegramBotService(TelegramProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(40000);
        this.restTemplate = new RestTemplate(factory);
    }

    private int lastUpdateId = 0;

    public void sendMessage(String text) {
        String botToken = properties.getToken();
        String chatId = properties.getChatId();

        if (botToken == null || botToken.isEmpty() || chatId == null || chatId.isEmpty()) {
            log.warn("Telegram bot token or chat ID is missing. Logging message instead:\n{}", text);
            return;
        }

        String url = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");

        try {
            restTemplate.postForEntity(url, body, String.class);
            log.info("Daily summary sent to Telegram successfully.");
        } catch (Exception e) {
            log.error("Failed to send message to Telegram", e);
        }
    }

    /**
     * Polls for new commands from the authorized user.
     * 
     * @param command The command to look for (e.g., "/totusg")
     * @return true if the command was found in new messages
     */
    public boolean hasCommand(String command) {
        String botToken = properties.getToken();
        String authorizedChatId = properties.getChatId();

        if (botToken == null || botToken.isEmpty()) {
            return false;
        }

        // Use offset to only get new updates, and timeout=30 for long polling
        String url = String.format("https://api.telegram.org/bot%s/getUpdates?offset=%d&timeout=30",
                botToken, lastUpdateId + 1);

        log.info("Polling Telegram for updates (offset: {})...", lastUpdateId + 1);
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            log.debug("Telegram response: {}", response.getBody());
            JsonNode root = objectMapper.readTree(response.getBody());

            if (root != null && root.has("result") && root.get("result").isArray()) {
                boolean found = false;
                for (JsonNode update : root.get("result")) {
                    int updateId = update.get("update_id").asInt();
                    lastUpdateId = updateId;

                    if (update.has("message")) {
                        JsonNode message = update.get("message");
                        if (message.has("text") && message.has("chat")) {
                            String text = message.get("text").asText().trim();
                            String chatId = String.valueOf(message.get("chat").get("id").asLong());

                            log.info("Telegram Update: Received '{}' from Chat ID: {}", text, chatId);

                            // Check if it's our command from our authorized chat
                            // Handles "/totusg" and "/totusg@bot_name"
                            boolean isCommand = text.equalsIgnoreCase(command) || text.startsWith(command + "@");

                            if (isCommand) {
                                if (chatId.equals(authorizedChatId)) {
                                    log.info("ACCEPTED: Command {} matched for authorized chat {}", command,
                                            authorizedChatId);
                                    found = true;
                                } else {
                                    log.warn("REJECTED: Command {} from UNAUTHORIZED chat ID: {}. Expected: {}",
                                            command, chatId, authorizedChatId);
                                }
                            }
                        }
                    }
                }
                return found;
            }
        } catch (Exception e) {
            log.error("Error polling Telegram updates: {}", e.getMessage());
        }

        return false;
    }
}
