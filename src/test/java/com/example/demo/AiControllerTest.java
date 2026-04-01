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

// Импорты для работы с результатами MockMvc
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Импорты для настройки заглушек внешних API
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(properties = {
        "steam.service.url=http://localhost:8081/stats/",
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
    @DisplayName("Сценарий: Успешное получение сводки профиля через Steam и OpenRouter")
    void testFullSummarizeFlow() throws Exception {

        // --- GIVEN ---
        String steamId = "76561198000000000";

        String mockSteamResponse = """
                {
                    "nickname": "Gamer123",
                    "hours": 150,
                    "games": ["Dota 2", "CS:GO"]
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

        // ожидание запроса к сервису статистики
        mockServer.expect(requestTo("http://localhost:8081/stats/" + steamId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mockSteamResponse, MediaType.APPLICATION_JSON));

        // ожидание запроса к OpenRouter
        mockServer.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockAiResponse, MediaType.APPLICATION_JSON));

        // --- WHEN ---
        var resultActions = mockMvc.perform(get("/api/ai/summaries/" + steamId));

        // --- THEN ---
        resultActions
                .andDo(print()) // Выведет детали запроса/ответа в консоль для отладки
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Этот игрок — ветеран киберспорта"));

        mockServer.verify();
    }
}