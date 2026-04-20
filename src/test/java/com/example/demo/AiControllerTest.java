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
    @DisplayName("Сценарий: Успешное получение сводки через GET к Steam и POST к OpenRouter")
    void testFullSummarizeFlow() throws Exception {
        String telegramId = "123456789";
        // Теперь мы ждем URL с параметром
        String expectedSteamUrl = "http://localhost:8080/api/ai/stats?telegramId=" + telegramId;

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

        // 1. Исправлено: GET вместо POST и новый URL
        mockServer.expect(requestTo(expectedSteamUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mockSteamResponse, MediaType.APPLICATION_JSON));

        // 2. OpenRouter остается POST (тут ничего не меняли)
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
        String expectedSteamUrl = "http://localhost:8080/api/ai/stats?telegramId=" + telegramId;

        // Исправлено: GET
        mockServer.expect(requestTo(expectedSteamUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        mockMvc.perform(get("/api/ai/summaries/" + telegramId))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message", containsString("Ошибка внешнего сервиса")));
    }

    @Test
    @DisplayName("Ошибка: Сбой нейросети (OpenRouter вернул 500)")
    void testAiServiceError() throws Exception {
        String telegramId = "123";
        String expectedSteamUrl = "http://localhost:8080/api/ai/stats?telegramId=" + telegramId;
        String mockSteamResponse = "{\"nickname\": \"Gamer123\"}";

        // 1. Стим возвращает успех через GET
        mockServer.expect(requestTo(expectedSteamUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mockSteamResponse, MediaType.APPLICATION_JSON));

        // 2. OpenRouter возвращает 500
        mockServer.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        mockMvc.perform(get("/api/ai/summaries/" + telegramId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.summary", containsString("Ошибка нейросети")));
    }
}