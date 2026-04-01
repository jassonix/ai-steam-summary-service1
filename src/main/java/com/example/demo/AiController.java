package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Value("${steam.service.url}")
    private String steamServiceUrl;

    @GetMapping("/summaries/{id}")
    public Map<String, Object> summarizeById(@PathVariable("id") String id) {
        try {
            // 1. Формируем URL для получения данных от Steam-сервиса
            String fetchUrl = steamServiceUrl + id;

            // 2. Делаем запрос к сервису-хранилищу данных
            // Мы ожидаем получить Map (JSON), который потом скормим нейросети
            ResponseEntity<Map> steamDataResponse = restTemplate.getForEntity(fetchUrl, Map.class);

            if (steamDataResponse.getStatusCode() != HttpStatus.OK || steamDataResponse.getBody() == null) {
                return Map.of("error", "Не удалось получить данные игрока с ID: " + id);
            }

            Map<String, Object> steamJson = (Map<String, Object>) steamDataResponse.getBody();

            // 3. Вызываем метод анализа
            return summarize(steamJson);

        } catch (Exception e) {
            return Map.of("error", "Ошибка при получении данных: " + e.getMessage());
        }
    }


    private final RestTemplate restTemplate;

    @Value("${ai.api.key}")
    private String apiKey;

    public AiController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostMapping(value = "/summaries", produces = "application/json; charset=UTF-8")
    public Map<String, Object> summarize(@RequestBody Map<String, Object> inputJson) {
        String url = "https://openrouter.ai/api/v1/chat/completions";

        // 1. Готовим заголовки (API ключ)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        // 2. Формируем запрос для нейронки
        Map<String, Object> requestBody = Map.of(
                "model", "stepfun/step-3.5-flash:free",
                "messages", List.of(Map.of("role", "user", "content", "Сделай сводку этого steam аккаунта, добавь что нибудь от себя: " + inputJson.toString()))
        );

        // 3. Отправляем и получаем ответ
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        // 4. Достаем текст ответа и отдаем обратно в JSON
        List choices = (List) response.getBody().get("choices");
        Map message = (Map) ((Map) choices.get(0)).get("message");

        return Map.of("result", message.get("content"));
    }
}