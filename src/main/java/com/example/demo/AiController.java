package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    @Value("${steam.service.url}")
    private String steamServiceUrl;

    private final RestTemplate restTemplate;

    @Value("${ai.api.key}")
    private String apiKey;

    public AiController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/summaries/{telegramId}")
    public ResponseEntity<Map<String, Object>> summarizeByTelegramId(@PathVariable("telegramId") String telegramId) {
        long startTime = System.currentTimeMillis();
        log.info("[START] Request for summary. TelegramId: {}", telegramId);

        try {
            Map<String, Object> steamRequest = Map.of("telegramId", telegramId);
            log.debug("Calling Steam Service at: {}. Payload: {}", steamServiceUrl, steamRequest);

            ResponseEntity<Map> steamDataResponse = restTemplate.postForEntity(steamServiceUrl, steamRequest, Map.class);

            if (steamDataResponse.getStatusCode() != HttpStatus.OK || steamDataResponse.getBody() == null) {
                log.error("[FAIL] Steam Service returned status: {} for TelegramId: {}",
                        steamDataResponse.getStatusCode(), telegramId);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("status", "error", "message", "Нет данных от Стим-сервиса"));
            }

            Map<String, Object> steamJson = (Map<String, Object>) steamDataResponse.getBody();
            log.info("[STEP 1] Data received from Steam. Nickname: '{}', Hours: {}, Top Game: '{}'",
                    steamJson.get("nickname"), steamJson.get("hoursTotal"), steamJson.get("topGame"));

            // Генерируем сводку
            Map<String, Object> result = summarize(steamJson);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[END] Request processed successfully in {} ms for TelegramId: {}", duration, telegramId);

            return ResponseEntity.ok(result);

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("[API ERROR] External service call failed. Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("status", "error", "message", "Ошибка внешнего сервиса"));
        } catch (Exception e) {
            log.error("[CRITICAL ERROR] Unexpected failure for TelegramId: " + telegramId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping(value = "/summaries", produces = "application/json; charset=UTF-8")
    public Map<String, Object> summarize(@RequestBody Map<String, Object> inputJson) {
        long aiStartTime = System.currentTimeMillis();
        log.info("[AI START] Beginning OpenRouter generation...");

        try {
            String url = "https://openrouter.ai/api/v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            String prompt = "Сделай краткую сводку этого steam аккаунта на русском языке, чтобы было понятно, блатной он или лох: " + inputJson.toString();

            Map<String, Object> requestBody = Map.of(
                    "model", "nvidia/nemotron-3-super-120b-a12b:free",
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            log.debug("Prompt sent to AI: {}", prompt);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            // Безопасное извлечение данных
            List choices = (List) response.getBody().get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("OpenRouter returned empty choices");
            }

            Map message = (Map) ((Map) choices.get(0)).get("message");
            String aiContent = (String) message.get("content");

            long aiDuration = System.currentTimeMillis() - aiStartTime;
            log.info("[AI END] Generation complete. Time taken: {} ms. Summary length: {} chars.",
                    aiDuration, aiContent.length());

            return Map.of(
                    "status", "success",
                    "summary", aiContent
            );

        } catch (Exception e) {
            log.error("[AI ERROR] Failed to generate summary with OpenRouter", e);
            return Map.of(
                    "status", "error",
                    "summary", "Ошибка нейросети: " + e.getMessage()
            );
        }
    }
}