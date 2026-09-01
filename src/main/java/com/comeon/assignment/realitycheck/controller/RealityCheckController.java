package com.comeon.assignment.realitycheck.controller;

import com.comeon.assignment.realitycheck.model.AcknowledgementResponse;
import com.comeon.assignment.realitycheck.model.ApiErrorResponse;
import com.comeon.assignment.realitycheck.model.RealityCheckSessionRequest;
import com.comeon.assignment.realitycheck.model.RealityCheckSessionResponse;
import com.comeon.assignment.realitycheck.service.RealityCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/players/{playerId}/reality-check-session")
@RequiredArgsConstructor
@Tag(name = "Reality-check sessions", description = "Manage a player's active reality-check session.")
public class RealityCheckController {

    private final RealityCheckService service;

    @PostMapping
    @Operation(summary = "Start a reality-check session",
            description = "Creates an ACTIVE session for a player who does not already have one.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session created",
                    content = @Content(schema = @Schema(implementation = RealityCheckSessionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid reminder interval",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Player not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Player already has an active session",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RealityCheckSessionResponse> createSession(
            @Parameter(description = "Player identifier", example = "1003") @PathVariable long playerId,
            @Valid @RequestBody RealityCheckSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createSession(playerId, request.intervalMinutes()));
    }

    @GetMapping
    @Operation(summary = "Get the active reality-check session",
            description = "Returns the player's current ACTIVE session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active session returned",
                    content = @Content(schema = @Schema(implementation = RealityCheckSessionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Player or active session not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public RealityCheckSessionResponse getActiveSession(
            @Parameter(description = "Player identifier", example = "1001") @PathVariable long playerId) {
        return service.getActiveSessionResponse(playerId);
    }

    @PatchMapping
    @Operation(summary = "Change the active session reminder interval",
            description = "Updates the interval and schedules the next reminder from the update time.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session updated",
                    content = @Content(schema = @Schema(implementation = RealityCheckSessionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid reminder interval",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Player or active session not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Session changed concurrently",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public RealityCheckSessionResponse updateSession(
            @Parameter(description = "Player identifier", example = "1001") @PathVariable long playerId,
            @Valid @RequestBody RealityCheckSessionRequest request) {
        return service.updateActiveSession(playerId, request.intervalMinutes());
    }

    @DeleteMapping
    @Operation(summary = "Stop the active reality-check session",
            description = "Marks the active session STOPPED and records its final elapsed time.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session stopped",
                    content = @Content(schema = @Schema(implementation = RealityCheckSessionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Player or active session not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Session changed concurrently",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public RealityCheckSessionResponse stopSession(
            @Parameter(description = "Player identifier", example = "1001") @PathVariable long playerId) {
        return service.stopActiveSession(playerId);
    }

    @PostMapping("/acknowledgements")
    @Operation(summary = "Record a reality-check acknowledgement",
            description = "Appends an acknowledgement-history row and returns the session with its latest acknowledgement time.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Acknowledgement recorded",
                    content = @Content(schema = @Schema(implementation = AcknowledgementResponse.class))),
            @ApiResponse(responseCode = "404", description = "Player or active session not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Session changed concurrently",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<AcknowledgementResponse> acknowledge(
            @Parameter(description = "Player identifier", example = "1001") @PathVariable long playerId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.acknowledge(playerId));
    }
}
