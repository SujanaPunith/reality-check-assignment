package com.comeon.assignment.realitycheck.service;

import com.comeon.assignment.realitycheck.exception.RealityCheckException;
import com.comeon.assignment.realitycheck.model.PlayerRecord;
import com.comeon.assignment.realitycheck.model.RealityCheckSession;
import com.comeon.assignment.realitycheck.model.RestResponse;
import com.comeon.assignment.realitycheck.repository.RealityCheckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RealityCheckService {
    private static final String ACTIVE = "ACTIVE";

    private final RealityCheckRepository repository;

    private final Map<Long, RealityCheckSession> cache = new ConcurrentHashMap<>();

    public String getStatus(long playerId) {
        RealityCheckSession s = cache.get(playerId);
        if (s == null) {
            s = repository.findByPlayerAndStatus(playerId, ACTIVE).orElse(null);
        }
        if (s == null) {
            log.error("No active reality check found for player {}", playerId);
            return null;
        }
        cache.put(playerId, s);
        return s.getStatus();
    }

    public PlayerRecord findPlayer(long playerId) {
        return repository.findPlayerFull(playerId);
    }

    public void insertSession(RealityCheckSession s) {
        repository.insertSession(s);
    }

    public RestResponse updateSession(long playerId, int intervalMinutes) {
        RealityCheckSession s = cache.get(playerId);
        if (s == null) {
            s = repository.findByPlayerAndStatus(playerId, ACTIVE).orElse(null);
        }
        if (s == null) {
            return new RestResponse("NO_ACTIVE_SESSION", null);
        }

        handle(s, intervalMinutes);
        repository.updateSession(s);

        RealityCheckSession x = repository.findByPlayerAndStatus(playerId, ACTIVE).orElse(null);
        cache.put(playerId, x);
        return new RestResponse(x);
    }

    public RealityCheckSession stopSession(long playerId) throws RealityCheckException {
        RealityCheckSession s = cache.get(playerId);
        if (s == null) {
            s = repository.findByPlayerAndStatus(playerId, ACTIVE).orElse(null);
        }
        if (s == null) {
            throw new RealityCheckException("NO_ACTIVE_SESSION");
        }
        s.setStatus("STOPPED");
        repository.updateSession(s);
        cache.remove(playerId);
        return s;
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
        s.setElapsedSeconds(now - s.getStartedAt());
        boolean promptDue = now >= s.getNextCheckAt();
        if (promptDue) {
            s.setLastPromptAt(now);
            s.setNextCheckAt(now + (long) s.getIntervalMinutes() * 60);
        }
        repository.updateSession(s);
        cache.put(playerId, s);
        return promptDue ? Optional.of(s) : Optional.empty();
    }

    public RealityCheckSession getActiveSession(long playerId) {
        RealityCheckSession s = cache.get(playerId);
        if (s == null) {
            s = repository.findByPlayerAndStatus(playerId, ACTIVE).orElse(null);
        }
        return s;
    }

    private void handle(RealityCheckSession s, int intervalMinutes) {
        long now = Instant.now().getEpochSecond();
        long x = now - s.getStartedAt();
        s.setElapsedSeconds(x);
        s.setIntervalMinutes(intervalMinutes);
        if (now >= s.getNextCheckAt()) {
            s.setLastPromptAt(now);
            s.setNextCheckAt(now + (long) intervalMinutes * 60);
        }
    }
}
