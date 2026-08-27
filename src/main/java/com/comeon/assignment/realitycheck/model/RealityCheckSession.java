package com.comeon.assignment.realitycheck.model;

import lombok.Data;

@Data
public class RealityCheckSession {
    private long id;
    private long playerId;
    private long franchiseId;
    private String status;
    private int intervalMinutes;
    private long startedAt;
    private long lastPromptAt;
    private long elapsedSeconds;
    private long netAmountMinor;
    private long nextCheckAt;
}
