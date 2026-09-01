package com.comeon.assignment.realitycheck.model;

public record RealityCheckSessionResponse(
        long id,
        long playerId,
        long franchiseId,
        String status,
        int intervalMinutes,
        String startedAt,
        String lastPromptAt,
        String acknowledgedAt,
        String nextCheckAt,
        long elapsedSeconds,
        long netAmountMinor) {
}
