package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AiController {

    private final RestTemplate restTemplate;

    @Value("${ai.api.key}") // Ключ подтянется из настроек
    private String apiKey;

    public AiController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostMapping(value = "/process", produces = "application/json; charset=UTF-8")
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