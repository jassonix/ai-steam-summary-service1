package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Value("${steam.service.url}")
    private String steamServiceUrl;

    private final RestTemplate restTemplate;

    @Value("${ai.api.key}")
    private String apiKey;

    public AiController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/summaries/{id}")
    // Возвращаем ResponseEntity вместо простой Map
    public ResponseEntity<Map<String, Object>> summarizeById(@PathVariable("id") String id) {
        try {
            String fetchUrl = steamServiceUrl + id;
            ResponseEntity<Map> steamDataResponse = restTemplate.getForEntity(fetchUrl, Map.class);

            // Проверка на пустой ответ (теперь тоже возвращаем статус, а не просто текст)
            if (steamDataResponse.getStatusCode() != HttpStatus.OK || steamDataResponse.getBody() == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("error", "Не удалось получить данные игрока с ID: " + id));
            }

            Map<String, Object> steamJson = (Map<String, Object>) steamDataResponse.getBody();

            // Вызываем метод анализа и оборачиваем его результат в ResponseEntity.ok()
            Map<String, Object> result = summarize(steamJson);
            return ResponseEntity.ok(result);

        } catch (HttpClientErrorException.NotFound e) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Профиль не найден"));
        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Произошла системная ошибка: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/summaries", produces = "application/json; charset=UTF-8")
    public Map<String, Object> summarize(@RequestBody Map<String, Object> inputJson) {
        String url = "https://openrouter.ai/api/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = Map.of(
                "model", "nvidia/nemotron-3-super-120b-a12b:free",
                "messages", List.of(Map.of("role", "user", "content", "Сделай сводку этого steam аккаунта: " + inputJson.toString()))
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        List choices = (List) response.getBody().get("choices");
        Map message = (Map) ((Map) choices.get(0)).get("message");

        return Map.of("result", message.get("content"));
    }
}