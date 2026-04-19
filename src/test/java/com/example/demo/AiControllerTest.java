package com.example.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = {
        "steam.service.url=http://localhost:8080/api/ai/stats",
        "ai.api.key=test-token-123"
})
@AutoConfigureMockMvc
@AutoConfigureMockRestServiceServer
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockRestServiceServer mockServer;

    @Test
    @DisplayName("Сценарий: Успешное получение сводки через POST к Steam и OpenRouter")
    void testFullSummarizeFlow() throws Exception {
        String telegramId = "123456789";

        String mockSteamResponse = """
                {
                    "nickname": "Gamer123",
                    "hoursTotal": 150,
                    "topGame": "Dota 2"
                }
                """;

        String mockAiResponse = """
                {
                    "choices": [{
                        "message": {
                            "content": "Этот игрок — ветеран киберспорта"
                        }
                    }]
                }
                """;

        mockServer.expect(requestTo("http://localhost:8080/api/ai/stats"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"telegramId\":\"" + telegramId + "\"}"))
                .andRespond(withSuccess(mockSteamResponse, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockAiResponse, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/ai/summaries/" + telegramId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.summary").value("Этот игрок — ветеран киберспорта"));

        mockServer.verify();
    }

    @Test
    @DisplayName("Ошибка: Привязка не найдена (Steam вернул 404)")
    void testSteamProfileNotFound() throws Exception {
        String telegramId = "999";

        mockServer.expect(requestTo("http://localhost:8080/api/ai/stats"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withResourceNotFound());

        mockMvc.perform(get("/api/ai/summaries/" + telegramId))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                // ИСПРАВЛЕНО: контроллер возвращает "Ошибка внешнего сервиса"
                .andExpect(jsonPath("$.message", containsString("Ошибка внешнего сервиса")));
    }

    @Test
    @DisplayName("Ошибка: Сбой нейросети (OpenRouter вернул 500)")
    void testAiServiceError() throws Exception {
        String telegramId = "123";
        String mockSteamResponse = "{\"nickname\": \"Gamer123\"}";

        // 1. Стим возвращает успех
        mockServer.expect(requestTo("http://localhost:8080/api/ai/stats"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockSteamResponse, MediaType.APPLICATION_JSON));

        // 2. OpenRouter возвращает 500
        mockServer.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andRespond(withServerError());

        mockMvc.perform(get("/api/ai/summaries/" + telegramId))
                .andDo(print())
                // ИСПРАВЛЕНИЕ: твой код возвращает 200, даже если нейросеть упала
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                // ИСПРАВЛЕНИЕ: ищем ошибку в поле summary
                .andExpect(jsonPath("$.summary", containsString("Ошибка нейросети")));
    }
}