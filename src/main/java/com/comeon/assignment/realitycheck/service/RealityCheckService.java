package com.comeon.assignment.realitycheck.service;

import com.comeon.assignment.realitycheck.exception.RealityCheckException;
import com.comeon.assignment.realitycheck.model.PlayerRecord;
import com.comeon.assignment.realitycheck.model.RealityCheckSession;
import com.comeon.assignment.realitycheck.model.RealityCheckSessionResponse;
import com.comeon.assignment.realitycheck.model.AcknowledgementResponse;
import com.comeon.assignment.realitycheck.repository.RealityCheckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RealityCheckService {
    private static final String ACTIVE = "ACTIVE";
    private static final String STOPPED = "STOPPED";
    private static final int MAX_INTERVAL_MINUTES = 24 * 60;

    private final RealityCheckRepository repository;
    private final RealityCheckTimeFormatter timeFormatter;


    public RealityCheckSessionResponse createSession(long playerId, int intervalMinutes) {
        validateInterval(intervalMinutes);
        PlayerRecord player = requirePlayer(playerId);
        if (getActiveSession(playerId) != null) {
            throw new RealityCheckException("ACTIVE_SESSION_ALREADY_EXISTS");
        }
        long now = Instant.now().getEpochSecond();
        RealityCheckSession session = new RealityCheckSession();
        session.setPlayerId(playerId);
        session.setFranchiseId(player.franchiseId);
        session.setStatus(ACTIVE);
        session.setIntervalMinutes(intervalMinutes);
        session.setStartedAt(now);
        session.setLastPromptAt(now);
        session.setElapsedSeconds(0);
        session.setNetAmountMinor(0);
        session.setNextCheckAt(now + (long) intervalMinutes * 60);
        if (!repository.insertSession(session)) {
            throw new RealityCheckException("ACTIVE_SESSION_ALREADY_EXISTS");
        }
        return toResponse(session, player);
    }

    public RealityCheckSessionResponse getActiveSessionResponse(long playerId) {
        PlayerRecord player = requirePlayer(playerId);
        return toResponse(requireActiveSession(playerId), player);
    }

    public RealityCheckSessionResponse updateActiveSession(long playerId, int intervalMinutes) {
        validateInterval(intervalMinutes);
        PlayerRecord player = requirePlayer(playerId);
        RealityCheckSession session = requireActiveSession(playerId);
        updateElapsedAndInterval(session, intervalMinutes, Instant.now().getEpochSecond());
        if (!repository.updateInterval(session)) {
            throw new RealityCheckException("SESSION_STATE_CONFLICT");
        }
        return toResponse(session, player);
    }


    public RealityCheckSessionResponse stopActiveSession(long playerId) {
        PlayerRecord player = requirePlayer(playerId);
        RealityCheckSession session = requireActiveSession(playerId);
        session.setElapsedSeconds(Instant.now().getEpochSecond() - session.getStartedAt());
        session.setStatus(STOPPED);
        if (!repository.stopSession(session)) {
            throw new RealityCheckException("SESSION_STATE_CONFLICT");
        }
        return toResponse(session, player);
    }

    public List<Long> activePlayerIds() {
        return repository.findActivePlayerIds();
    }

    public Optional<RealityCheckSession> refresh(long playerId) {
        RealityCheckSession s = repository.findByPlayerAndStatus(playerId, ACTIVE).orElse(null);
        if (s == null) {
            return Optional.empty();
        }
        long now = Instant.now().getEpochSecond();
        if (repository.claimDueSession(s, now)) {
            return Optional.of(s);
        }
        repository.updateElapsedSecondsForNonDueActiveSession(s.getId(), now);
        return Optional.empty();
    }

    private RealityCheckSession getActiveSession(long playerId) {
        return repository
                .findByPlayerAndStatus(playerId, ACTIVE)
                .orElse(null);
    }

    private void updateElapsedAndInterval(
            RealityCheckSession session,
            int intervalMinutes,
            long now) {
        session.setElapsedSeconds(now - session.getStartedAt());
        session.setNextCheckAt(now + (long) intervalMinutes * 60);
        session.setIntervalMinutes(intervalMinutes);
    }


    public AcknowledgementResponse acknowledge(long playerId) {
        PlayerRecord player = requirePlayer(playerId);
        RealityCheckSession session = requireActiveSession(playerId);
        session.setAcknowledgedAt(Instant.now().getEpochSecond());
        if (!repository.recordAcknowledgement(session)) {
            throw new RealityCheckException("SESSION_STATE_CONFLICT");
        }
        return new AcknowledgementResponse(toResponse(session, player));
    }

    private PlayerRecord requirePlayer(long playerId) {
        PlayerRecord player = repository.findPlayerFull(playerId);
        if (player == null) {
            throw new RealityCheckException("PLAYER_NOT_FOUND");
        }
        return player;
    }

    private RealityCheckSession requireActiveSession(long playerId) {
        RealityCheckSession session = getActiveSession(playerId);
        if (session == null) {
            throw new RealityCheckException("ACTIVE_SESSION_NOT_FOUND");
        }
        return session;
    }

    private RealityCheckSessionResponse toResponse(
            RealityCheckSession session,
            PlayerRecord player) {

        String timezone = player.timezone;

        return new RealityCheckSessionResponse(
                session.getId(),
                session.getPlayerId(),
                session.getFranchiseId(),
                session.getStatus(),
                session.getIntervalMinutes(),
                timeFormatter.format(session.getStartedAt(), timezone),
                timeFormatter.format(session.getLastPromptAt(), timezone),
                timeFormatter.format(session.getAcknowledgedAt(), timezone),
                timeFormatter.format(session.getNextCheckAt(), timezone),
                session.getElapsedSeconds(),
                session.getNetAmountMinor());
    }

    private void validateInterval(int intervalMinutes) {
        if (intervalMinutes <= 0 || intervalMinutes > MAX_INTERVAL_MINUTES) {
            throw new RealityCheckException("INVALID_INTERVAL_MINUTES");
        }
    }

}
