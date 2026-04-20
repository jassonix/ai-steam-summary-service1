package com.example.demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Аннотация игнорирует лишние поля из JSON, которых нет в этом классе
@JsonIgnoreProperties(ignoreUnknown = true)
public record SteamProfileDto(
        String nickname,
        Integer gamesCount,
        Long hoursTotal,
        String topGame,
        Integer steamLevel,
        Integer friendCount,
        String personState
) {}