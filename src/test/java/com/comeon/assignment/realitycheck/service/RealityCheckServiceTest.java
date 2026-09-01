package com.comeon.assignment.realitycheck.service;

import com.comeon.assignment.realitycheck.exception.RealityCheckException;
import com.comeon.assignment.realitycheck.model.PlayerRecord;
import com.comeon.assignment.realitycheck.model.RealityCheckSession;
import com.comeon.assignment.realitycheck.repository.RealityCheckRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealityCheckServiceTest {

    private final RealityCheckRepository repository = mock(RealityCheckRepository.class);
    private final RealityCheckService service =
            new RealityCheckService(repository, new RealityCheckTimeFormatter());

    @Test
    void rejectsNonPositiveAndExcessiveIntervals() {
        assertThatThrownBy(() -> service.createSession(1001, 0))
                .isInstanceOf(RealityCheckException.class)
                .hasMessage("INVALID_INTERVAL_MINUTES");
        assertThatThrownBy(() -> service.createSession(1001, -1))
                .isInstanceOf(RealityCheckException.class)
                .hasMessage("INVALID_INTERVAL_MINUTES");
        assertThatThrownBy(() -> service.createSession(1001, 1441))
                .isInstanceOf(RealityCheckException.class)
                .hasMessage("INVALID_INTERVAL_MINUTES");
    }

    @Test
    void createsSessionWithNextCheckCalculatedFromStartTime() throws Exception {
        PlayerRecord player = player(1001, 10);
        when(repository.findPlayerFull(1001)).thenReturn(player);
        when(repository.findByPlayerAndStatus(1001, "ACTIVE")).thenReturn(Optional.empty());
        when(repository.insertSession(any(RealityCheckSession.class))).thenReturn(true);

        long before = Instant.now().getEpochSecond();
        service.createSession(1001, 15);
        long after = Instant.now().getEpochSecond();

        ArgumentCaptor<RealityCheckSession> captor = ArgumentCaptor.forClass(RealityCheckSession.class);
        verify(repository).insertSession(captor.capture());
        RealityCheckSession created = captor.getValue();
        assertThat(created.getStartedAt()).isBetween(before, after);
        assertThat(created.getNextCheckAt()).isEqualTo(created.getStartedAt() + 15 * 60L);
    }

    @Test
    void mapsDatabaseActiveSessionConflictToDomainConflict() {
        when(repository.findPlayerFull(1001)).thenReturn(player(1001, 10));
        when(repository.findByPlayerAndStatus(1001, "ACTIVE")).thenReturn(Optional.empty());
        when(repository.insertSession(any(RealityCheckSession.class))).thenReturn(false);

        assertThatThrownBy(() -> service.createSession(1001, 15))
                .isInstanceOf(RealityCheckException.class)
                .hasMessage("ACTIVE_SESSION_ALREADY_EXISTS");
    }

    @Test
    void retrievesAnActiveSession() {
        RealityCheckSession session = activeSession(1001, 1_700L);
        when(repository.findPlayerFull(1001)).thenReturn(player(1001, 10));
        when(repository.findByPlayerAndStatus(1001, "ACTIVE")).thenReturn(Optional.of(session));

        assertThat(service.getActiveSessionResponse(1001).status()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsAnUnknownPlayer() {
        when(repository.findPlayerFull(9999)).thenReturn(null);

        assertThatThrownBy(() -> service.getActiveSessionResponse(9999))
                .isInstanceOf(RealityCheckException.class)
                .hasMessage("PLAYER_NOT_FOUND");
    }

    @Test
    void rejectsAPlayerWithoutAnActiveSession() {
        when(repository.findPlayerFull(1003)).thenReturn(player(1003, 20));
        when(repository.findByPlayerAndStatus(1003, "ACTIVE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveSessionResponse(1003))
                .isInstanceOf(RealityCheckException.class)
                .hasMessage("ACTIVE_SESSION_NOT_FOUND");
    }

    @Test
    void changesIntervalAndSchedulesNextCheckFromChangeTime() throws Exception {
        RealityCheckSession session = activeSession(1001, Instant.now().getEpochSecond() - 120);
        when(repository.findPlayerFull(1001)).thenReturn(player(1001, 10));
        when(repository.findByPlayerAndStatus(1001, "ACTIVE")).thenReturn(Optional.of(session));
        when(repository.updateInterval(session)).thenReturn(true);

        long before = Instant.now().getEpochSecond();
        service.updateActiveSession(1001, 30);
        long after = Instant.now().getEpochSecond();

        assertThat(session.getIntervalMinutes()).isEqualTo(30);
        assertThat(session.getNextCheckAt()).isBetween(before + 30 * 60L, after + 30 * 60L);
        assertThat(session.getElapsedSeconds()).isGreaterThanOrEqualTo(120);
    }

    @Test
    void stoppingSessionRecordsElapsedTimeBeforeChangingStatus() throws Exception {
        long startedAt = Instant.now().getEpochSecond() - 120;
        RealityCheckSession session = activeSession(1001, startedAt);
        when(repository.findByPlayerAndStatus(1001, "ACTIVE")).thenReturn(Optional.of(session));
        when(repository.stopSession(session)).thenReturn(true);

        when(repository.findPlayerFull(1001)).thenReturn(player(1001, 10));
        service.stopActiveSession(1001);

        assertThat(session.getStatus()).isEqualTo("STOPPED");
        assertThat(session.getElapsedSeconds()).isGreaterThanOrEqualTo(120);
        verify(repository).stopSession(session);
    }

    @Test
    void staleRefreshDoesNotProduceAnEventWhenActiveWriteIsRejected() {
        RealityCheckSession session = activeSession(1001, Instant.now().getEpochSecond() - 120);
        session.setNextCheckAt(Instant.now().getEpochSecond() - 1);
        when(repository.findByPlayerAndStatus(1001, "ACTIVE")).thenReturn(Optional.of(session));
        when(repository.claimDueSession(any(RealityCheckSession.class), any(Long.class))).thenReturn(false);

        assertThat(service.refresh(1001)).isEmpty();
    }

    @Test
    void acknowledgeRecordsHistoryThroughTheTransactionalRepositoryOperation() throws Exception {
        RealityCheckSession session = activeSession(1001, Instant.now().getEpochSecond() - 120);
        PlayerRecord player = player(1001, 10);
        player.timezone = "Europe/Stockholm";
        when(repository.findByPlayerAndStatus(1001, "ACTIVE")).thenReturn(Optional.of(session));
        when(repository.recordAcknowledgement(session)).thenReturn(true);
        when(repository.findPlayerFull(1001)).thenReturn(player);

        service.acknowledge(1001);

        assertThat(session.getAcknowledgedAt()).isNotNull();
        verify(repository).recordAcknowledgement(session);
    }

    @Test
    void formatsSessionTimestampsInThePlayersTimezone() {
        long instant = Instant.parse("2026-07-06T12:35:00Z").getEpochSecond();
        RealityCheckSession session = activeSession(1001, instant);
        PlayerRecord player = player(1001, 10);
        player.timezone = "Europe/Stockholm";
        when(repository.findPlayerFull(1001)).thenReturn(player);
        when(repository.findByPlayerAndStatus(1001, "ACTIVE")).thenReturn(Optional.of(session));

        assertThat(service.getActiveSessionResponse(1001).startedAt())
                .isEqualTo("6 July 26 14:35");
    }

    private static PlayerRecord player(long id, long franchiseId) {
        PlayerRecord player = new PlayerRecord();
        player.id = id;
        player.franchiseId = franchiseId;
        player.timezone = "UTC";
        return player;
    }

    private static RealityCheckSession activeSession(long playerId, long startedAt) {
        RealityCheckSession session = new RealityCheckSession();
        session.setId(1);
        session.setPlayerId(playerId);
        session.setFranchiseId(10);
        session.setStatus("ACTIVE");
        session.setIntervalMinutes(10);
        session.setStartedAt(startedAt);
        session.setLastPromptAt(startedAt);
        session.setNextCheckAt(startedAt + 600);
        return session;
    }
}
