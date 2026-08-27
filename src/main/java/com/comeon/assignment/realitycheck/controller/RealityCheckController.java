package com.comeon.assignment.realitycheck.controller;

import com.comeon.assignment.realitycheck.exception.RealityCheckException;
import com.comeon.assignment.realitycheck.model.PlayerRecord;
import com.comeon.assignment.realitycheck.model.RealityCheckSession;
import com.comeon.assignment.realitycheck.model.RestResponse;
import com.comeon.assignment.realitycheck.service.RealityCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@Slf4j
@RequiredArgsConstructor
public class RealityCheckController {
    private final RealityCheckService service;

    @GetMapping("/realitycheck/getStatus/{playerId}")
    @ResponseBody
    public String getStatus(@PathVariable long playerId) {
        return service.getStatus(playerId);
    }

    @GetMapping("/realitycheck/getOrStartSession/{playerId}/{intervalMinutes}")
    @ResponseBody
    public RestResponse getOrStartSession(@PathVariable long playerId, @PathVariable int intervalMinutes) {
        try {
            PlayerRecord p = service.findPlayer(playerId);
            if (p == null) {
                throw new RealityCheckException("PLAYER_NOT_FOUND");
            }
            long franchiseId = p.franchiseId;

            RealityCheckSession s = service.getActiveSession(playerId);

            boolean flag = s == null;
            if (flag) {
                long now = Instant.now().getEpochSecond();
                RealityCheckSession tmp = new RealityCheckSession();
                tmp.setPlayerId(playerId);
                tmp.setFranchiseId(franchiseId);
                tmp.setStatus("ACTIVE");
                tmp.setIntervalMinutes(intervalMinutes);
                tmp.setStartedAt(now);
                tmp.setLastPromptAt(now);
                tmp.setElapsedSeconds(0);
                tmp.setNetAmountMinor(0);
                tmp.setNextCheckAt(now + (long) intervalMinutes * 60);
                service.insertSession(tmp);

                return new RestResponse(service.getStatus(playerId));
            }

            if (s.getFranchiseId() != franchiseId) {
                throw new RealityCheckException("FRANCHISE_MISMATCH");
            }

            return service.updateSession(playerId, intervalMinutes);
        } catch (RealityCheckException e) {
            log.error("getOrStartSession failed for player {}", playerId, e);
            return new RestResponse(e.getMessage(), e);
        }
    }

    @PostMapping("/realitycheck/stopSession/{playerId}")
    @ResponseBody
    public RestResponse stopSession(@PathVariable long playerId) {
        try {
            RealityCheckSession s = service.stopSession(playerId);
            return new RestResponse(s);
        } catch (RealityCheckException e) {
            return new RestResponse(e.getMessage(), e);
        }
    }
}
