package com.comeon.assignment.realitycheck.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record RealityCheckSessionRequest(
        @Schema(description = "Reminder interval in minutes.", minimum = "1", maximum = "1440", example = "30")
        @NotNull @Min(1) @Max(1440) Integer intervalMinutes) {
}
