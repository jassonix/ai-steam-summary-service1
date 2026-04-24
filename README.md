# Steam AI Summary Service

Микросервис на Spring Boot для анализа игровой статистики Steam и генерации сводок с помощью нейросетей через API OpenRouter/DeepSeek.

## Технологический стек
* Java 21
* Spring Boot 3.2.4
* Maven
* JUnit 5 / MockMvc / MockRestServiceServer

## Настройка окружения
Для работы сервиса необходимо задать следующие переменные окружения (в файле .env или настройках системы):

* `STEAM_SERVICE_URL` — базовый URL сервиса статистики (без /stats/). Пример: `http://localhost:8080/api/ai`
* `AI_API_KEY` — API-ключ OpenRouter.
* `AI_SYSTEM_TEMPLATE` — шаблон системного промпта. Поддерживает форматирование через `%s`.

## Сборка и запуск
Сборка проекта и выполнение тестов:
```bash
mvn clean package
```

Запуск приложения:
```bash
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

## API Эндпоинты
### Получение сводки игрока
`GET /api/ai/summaries/{telegramId}`

**Пример успешного ответа:**
```json
{
  "status": "success",
  "summary": "Текст характеристики игрока от ИИ"
}
```

## Тестирование
Для запуска интеграционных тестов используйте команду:
```bash
mvn test
```
Тесты проверяют корректность формирования URL (Path Variable), обработку ответов Steam и сценарии ошибок
