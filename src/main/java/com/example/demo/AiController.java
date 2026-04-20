package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
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
        log.info("[START] Request for summary via DTO. TelegramId: {}", telegramId);

        try {
            String urlWithParam = UriComponentsBuilder
                    .fromHttpUrl(steamServiceUrl)
                    .queryParam("telegramId", telegramId)
                    .toUriString();

            // 1. Получаем данные сразу в DTO
            ResponseEntity<SteamProfileDto> steamDataResponse =
                    restTemplate.getForEntity(urlWithParam, SteamProfileDto.class);

            if (steamDataResponse.getStatusCode() != HttpStatus.OK || steamDataResponse.getBody() == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("status", "error", "message", "Нет данных от Стим-сервиса"));
            }

            SteamProfileDto profile = steamDataResponse.getBody();
            log.info("[STEP 1] DTO received. Nickname: '{}', Hours: {}", profile.nickname(), profile.hoursTotal());

            // 2. Вызываем summarize, передавая объект
            Map<String, Object> result = summarize(profile);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[END] Request processed in {} ms", duration);

            return ResponseEntity.ok(result);

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("[API ERROR] Status: {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("status", "error", "message", "Ошибка внешнего сервиса"));
        } catch (Exception e) {
            log.error("[CRITICAL ERROR]", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // Этот метод теперь тоже принимает DTO (через JSON в Body или напрямую из метода выше)
    @PostMapping(value = "/summaries", produces = "application/json; charset=UTF-8")
    public Map<String, Object> summarize(@RequestBody SteamProfileDto profile) {
        long aiStartTime = System.currentTimeMillis();
        log.info("[AI START] Beginning OpenRouter generation for {}", profile.nickname());

        try {
            String url = "https://openrouter.ai/api/v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // Теперь промпт чистый и понятный для ИИ
            String prompt = String.format(
                    "Сделай краткую сводку Steam аккаунта на русском языке. Далее пиши, имитируя разговорную речь, от первого лица и не стесняясь использовать слова, которые могут кому-то показаться обидными. Скажи, блатной это игрок или лох, и аргументируй.",
                    profile.nickname(), profile.gamesCount(), profile.hoursTotal(),
                    profile.steamLevel(), profile.friendCount()
            );

            Map<String, Object> requestBody = Map.of(
                    "model", "nvidia/nemotron-3-super-120b-a12b:free",
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            List choices = (List) response.getBody().get("choices");
            Map message = (Map) ((Map) choices.get(0)).get("message");
            String aiContent = (String) message.get("content");

            log.info("[AI END] Time taken: {} ms", (System.currentTimeMillis() - aiStartTime));

            return Map.of("status", "success", "summary", aiContent);

        } catch (Exception e) {
            log.error("[AI ERROR]", e);
            return Map.of("status", "error", "summary", "Ошибка нейросети: " + e.getMessage());
        }
    }
}