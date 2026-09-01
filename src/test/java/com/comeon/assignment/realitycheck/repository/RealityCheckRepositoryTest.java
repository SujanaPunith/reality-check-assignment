package com.comeon.assignment.realitycheck.repository;

import com.comeon.assignment.realitycheck.model.RealityCheckSession;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealityCheckRepositoryTest {

    private Jdbi jdbi;
    private RealityCheckRepository repository;

    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create("jdbc:h2:mem:repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbi.useHandle(handle -> {
            handle.execute("DROP TABLE IF EXISTS reality_check_acknowledgement");
            handle.execute("DROP TABLE IF EXISTS reality_check_session");
            handle.execute("""
                    CREATE TABLE reality_check_session (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        player_id BIGINT,
                        franchise_id BIGINT,
                        status VARCHAR(16),
                        interval_minutes INT,
                        started_at BIGINT,
                        last_prompt_at BIGINT,
                        elapsed_seconds BIGINT,
                        net_amount_minor BIGINT,
                        next_check_at BIGINT,
                        acknowledged_at BIGINT,
                        active_player_id BIGINT GENERATED ALWAYS AS
                            (CASE WHEN status = 'ACTIVE' THEN player_id ELSE NULL END),
                        CONSTRAINT uq_reality_check_session_active_player UNIQUE (active_player_id)
                    )
                    """);
            handle.execute("""
                    CREATE TABLE reality_check_acknowledgement (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        session_id BIGINT NOT NULL,
                        player_id BIGINT NOT NULL,
                        acknowledged_at BIGINT NOT NULL,
                        CONSTRAINT fk_acknowledgement_session
                            FOREIGN KEY (session_id) REFERENCES reality_check_session(id)
                    )
                    """);
        });
        repository = new RealityCheckRepository(jdbi);
    }

    @Test
    void allowsOneActiveSessionPerPlayer() {
        RealityCheckSession session = activeSession();
        assertThat(repository.insertSession(session)).isTrue();
        assertThat(session.getId()).isPositive();
        assertThat(countSessions(1001, "ACTIVE")).isOne();
    }

    @Test
    void rejectsASecondActiveSessionForTheSamePlayer() {
        assertThat(repository.insertSession(activeSession())).isTrue();
        assertThat(repository.insertSession(activeSession())).isFalse();
        assertThat(countSessions(1001, "ACTIVE")).isOne();
    }

    @Test
    void allowsMultipleStoppedSessionsForTheSamePlayer() {
        RealityCheckSession first = activeSession();
        first.setStatus("STOPPED");
        RealityCheckSession second = activeSession();
        second.setStatus("STOPPED");

        assertThat(repository.insertSession(first)).isTrue();
        assertThat(repository.insertSession(second)).isTrue();
        assertThat(countSessions(1001, "STOPPED")).isEqualTo(2);
    }

    @Test
    void allowsOnlyOneConcurrentActiveSessionCreation() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> firstInsert = executor.submit(() -> insertWhenStarted(ready, start));
            Future<Boolean> secondInsert = executor.submit(() -> insertWhenStarted(ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(firstInsert.get(), secondInsert.get()))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(countSessions(1001, "ACTIVE")).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsStaleActiveUpdateAfterSessionHasBeenStopped() {
        RealityCheckSession initial = activeSession();
        repository.insertSession(initial);
        RealityCheckSession staleActive = repository.findByPlayerAndStatus(1001, "ACTIVE").orElseThrow();
        RealityCheckSession current = repository.findByPlayerAndStatus(1001, "ACTIVE").orElseThrow();

        assertThat(repository.stopSession(current)).isTrue();

        staleActive.setElapsedSeconds(600);
        assertThat(repository.updateInterval(staleActive)).isFalse();
        assertThat(repository.findByPlayerAndStatus(1001, "STOPPED"))
                .get()
                .extracting(RealityCheckSession::getStatus, RealityCheckSession::getElapsedSeconds)
                .containsExactly("STOPPED", 0L);
    }

    @Test
    void recordsEveryAcknowledgementAndUpdatesTheLatestSessionTimestamp() {
        repository.insertSession(activeSession());
        RealityCheckSession session = repository.findByPlayerAndStatus(1001, "ACTIVE").orElseThrow();

        session.setAcknowledgedAt(1_700L);
        assertThat(repository.recordAcknowledgement(session)).isTrue();

        session.setAcknowledgedAt(1_800L);
        assertThat(repository.recordAcknowledgement(session)).isTrue();

        List<Long> acknowledgements = jdbi.withHandle(handle -> handle.createQuery("""
                SELECT acknowledged_at
                FROM reality_check_acknowledgement
                WHERE session_id = :sessionId
                ORDER BY id
                """)
                .bind("sessionId", session.getId())
                .mapTo(Long.class)
                .list());

        assertThat(acknowledgements).containsExactly(1_700L, 1_800L);
        assertThat(latestAcknowledgedAt(session.getId())).isEqualTo(1_800L);
    }

    @Test
    void olderAcknowledgementCannotReplaceTheLatestTimestampButRemainsInHistory() {
        RealityCheckSession session = insertAndFindActiveSession();
        session.setAcknowledgedAt(1_800L);
        assertThat(repository.recordAcknowledgement(session)).isTrue();
        session.setAcknowledgedAt(1_700L);
        assertThat(repository.recordAcknowledgement(session)).isTrue();

        assertThat(latestAcknowledgedAt(session.getId())).isEqualTo(1_800L);
        assertThat(acknowledgements(session.getId())).containsExactly(1_800L, 1_700L);
    }

    @Test
    void acknowledgementDoesNotRestoreStaleScheduleOrOverwriteLastPrompt() {
        RealityCheckSession stale = insertAndFindActiveSession();
        RealityCheckSession claimant = findActive();
        assertThat(repository.claimDueSession(claimant, 1_700L)).isTrue();

        stale.setAcknowledgedAt(1_750L);
        assertThat(repository.recordAcknowledgement(stale)).isTrue();

        RealityCheckSession stored = findActive();
        assertThat(stored.getNextCheckAt()).isEqualTo(2_300L);
        assertThat(stored.getLastPromptAt()).isEqualTo(1_700L);
        assertThat(stored.getAcknowledgedAt()).isEqualTo(1_750L);
    }

    @Test
    void intervalChangePreservesAcknowledgementAndLastPrompt() {
        RealityCheckSession stale = insertAndFindActiveSession();
        RealityCheckSession acknowledgement = findActive();
        acknowledgement.setAcknowledgedAt(1_650L);
        assertThat(repository.recordAcknowledgement(acknowledgement)).isTrue();
        assertThat(repository.claimDueSession(findActive(), 1_700L)).isTrue();

        stale.setIntervalMinutes(20);
        stale.setElapsedSeconds(750L);
        stale.setNextCheckAt(2_950L);
        assertThat(repository.updateInterval(stale)).isTrue();

        RealityCheckSession stored = findActive();
        assertThat(stored.getAcknowledgedAt()).isEqualTo(1_650L);
        assertThat(stored.getLastPromptAt()).isEqualTo(1_700L);
        assertThat(stored.getIntervalMinutes()).isEqualTo(20);
    }

    @Test
    void reminderClaimPreservesAcknowledgement() {
        RealityCheckSession session = insertAndFindActiveSession();
        session.setAcknowledgedAt(1_550L);
        assertThat(repository.recordAcknowledgement(session)).isTrue();

        assertThat(repository.claimDueSession(findActive(), 1_700L)).isTrue();
        assertThat(findActive().getAcknowledgedAt()).isEqualTo(1_550L);
    }

    @Test
    void stopPreservesAcknowledgement() {
        RealityCheckSession session = insertAndFindActiveSession();
        session.setAcknowledgedAt(1_550L);
        assertThat(repository.recordAcknowledgement(session)).isTrue();
        RealityCheckSession stale = findActive();
        stale.setElapsedSeconds(800L);

        assertThat(repository.stopSession(stale)).isTrue();
        RealityCheckSession stopped = repository.findByPlayerAndStatus(1001, "STOPPED").orElseThrow();
        assertThat(stopped.getAcknowledgedAt()).isEqualTo(1_550L);
    }

    @Test
    void concurrentClaimAndAcknowledgementPreserveClaimAndHistory() throws Exception {
        RealityCheckSession claim = insertAndFindActiveSession();
        RealityCheckSession acknowledgement = findActive();
        acknowledgement.setAcknowledgedAt(1_750L);

        List<Boolean> results = runConcurrently(
                () -> repository.claimDueSession(claim, 1_700L),
                () -> repository.recordAcknowledgement(acknowledgement));

        assertThat(results).containsOnly(true);
        RealityCheckSession stored = findActive();
        assertThat(stored.getNextCheckAt()).isEqualTo(2_300L);
        assertThat(stored.getLastPromptAt()).isEqualTo(1_700L);
        assertThat(stored.getAcknowledgedAt()).isEqualTo(1_750L);
        assertThat(acknowledgements(stored.getId())).containsExactly(1_750L);
    }

    @Test
    void concurrentClaimAndIntervalChangeNeverRestoreStaleSchedule() throws Exception {
        RealityCheckSession claim = insertAndFindActiveSession();
        RealityCheckSession interval = findActive();
        interval.setIntervalMinutes(20);
        interval.setElapsedSeconds(700L);
        interval.setNextCheckAt(2_900L);

        List<Boolean> results = runConcurrently(
                () -> repository.claimDueSession(claim, 1_700L),
                () -> repository.updateInterval(interval));

        RealityCheckSession stored = findActive();
        assertThat(results.get(1)).isTrue();
        assertThat(stored.getIntervalMinutes()).isEqualTo(20);
        assertThat(stored.getNextCheckAt()).isEqualTo(2_900L);
    }

    @Test
    void rejectsStaleClaimAfterIntervalHasChanged() {
        RealityCheckSession staleClaim = insertAndFindActiveSession();
        RealityCheckSession interval = findActive();
        interval.setIntervalMinutes(20);
        interval.setElapsedSeconds(700L);
        interval.setNextCheckAt(2_900L);

        assertThat(repository.updateInterval(interval)).isTrue();
        assertThat(repository.claimDueSession(staleClaim, 1_700L)).isFalse();

        RealityCheckSession stored = findActive();
        assertThat(stored.getIntervalMinutes()).isEqualTo(20);
        assertThat(stored.getNextCheckAt()).isEqualTo(2_900L);
    }

    @Test
    void concurrentAcknowledgementAndIntervalChangePreserveBothResults() throws Exception {
        insertAndFindActiveSession();
        RealityCheckSession acknowledgement = findActive();
        acknowledgement.setAcknowledgedAt(1_750L);
        RealityCheckSession interval = findActive();
        interval.setIntervalMinutes(20);
        interval.setElapsedSeconds(700L);
        interval.setNextCheckAt(2_900L);

        assertThat(runConcurrently(
                () -> repository.recordAcknowledgement(acknowledgement),
                () -> repository.updateInterval(interval))).containsOnly(true);

        RealityCheckSession stored = findActive();
        assertThat(stored.getAcknowledgedAt()).isEqualTo(1_750L);
        assertThat(stored.getIntervalMinutes()).isEqualTo(20);
        assertThat(stored.getNextCheckAt()).isEqualTo(2_900L);
        assertThat(acknowledgements(stored.getId())).containsExactly(1_750L);
    }

    @Test
    void rollsBackLatestAcknowledgementWhenHistoryInsertFails() {
        repository.insertSession(activeSession());
        RealityCheckSession session = repository.findByPlayerAndStatus(1001, "ACTIVE").orElseThrow();
        session.setAcknowledgedAt(1_700L);
        jdbi.useHandle(handle -> handle.execute("DROP TABLE reality_check_acknowledgement"));

        assertThatThrownBy(() -> repository.recordAcknowledgement(session))
                .isInstanceOf(RuntimeException.class);
        assertThat(latestAcknowledgedAt(session.getId())).isNull();
    }

    @Test
    void claimsADueActiveSession() {
        RealityCheckSession session = insertAndFindActiveSession();

        assertThat(repository.claimDueSession(session, 1_700L)).isTrue();
        assertThat(session.getElapsedSeconds()).isEqualTo(700L);
        assertThat(session.getLastPromptAt()).isEqualTo(1_700L);
        assertThat(session.getNextCheckAt()).isEqualTo(2_300L);
    }

    @Test
    void doesNotClaimANonDueSession() {
        RealityCheckSession session = insertAndFindActiveSession();

        assertThat(repository.claimDueSession(session, 1_500L)).isFalse();
    }

    @Test
    void doesNotClaimAStoppedSession() {
        repository.insertSession(activeSession());
        RealityCheckSession staleSession = repository.findByPlayerAndStatus(1001, "ACTIVE").orElseThrow();
        RealityCheckSession currentSession = repository.findByPlayerAndStatus(1001, "ACTIVE").orElseThrow();
        assertThat(repository.stopSession(currentSession)).isTrue();

        assertThat(repository.claimDueSession(staleSession, 1_700L)).isFalse();
    }

    @Test
    void allowsOnlyOneConcurrentClaim() throws Exception {
        repository.insertSession(activeSession());
        RealityCheckSession firstReplica = repository.findByPlayerAndStatus(1001, "ACTIVE").orElseThrow();
        RealityCheckSession secondReplica = repository.findByPlayerAndStatus(1001, "ACTIVE").orElseThrow();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> firstClaim = executor.submit(() -> claimWhenStarted(firstReplica, ready, start));
            Future<Boolean> secondClaim = executor.submit(() -> claimWhenStarted(secondReplica, ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(firstClaim.get(), secondClaim.get()))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean claimWhenStarted(
            RealityCheckSession session,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return repository.claimDueSession(session, 1_700L);
    }

    private boolean insertWhenStarted(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return repository.insertSession(activeSession());
    }

    private List<Boolean> runConcurrently(
            java.util.concurrent.Callable<Boolean> first,
            java.util.concurrent.Callable<Boolean> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstResult = executor.submit(() -> callWhenStarted(first, ready, start));
            Future<Boolean> secondResult = executor.submit(() -> callWhenStarted(second, ready, start));
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean callWhenStarted(
            java.util.concurrent.Callable<Boolean> operation,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return operation.call();
    }

    private long countSessions(long playerId, String status) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT COUNT(*)
                        FROM reality_check_session
                        WHERE player_id = :playerId AND status = :status
                        """)
                .bind("playerId", playerId)
                .bind("status", status)
                .mapTo(Long.class)
                .one());
    }

    private Long latestAcknowledgedAt(long sessionId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT acknowledged_at
                        FROM reality_check_session
                        WHERE id = :sessionId
                        """)
                .bind("sessionId", sessionId)
                .mapTo(Long.class)
                .findOne()
                .orElse(null));
    }

    private List<Long> acknowledgements(long sessionId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT acknowledged_at
                        FROM reality_check_acknowledgement
                        WHERE session_id = :sessionId
                        ORDER BY id
                        """)
                .bind("sessionId", sessionId)
                .mapTo(Long.class)
                .list());
    }

    private RealityCheckSession findActive() {
        return repository.findByPlayerAndStatus(1001, "ACTIVE").orElseThrow();
    }

    private RealityCheckSession insertAndFindActiveSession() {
        repository.insertSession(activeSession());
        return repository.findByPlayerAndStatus(1001, "ACTIVE").orElseThrow();
    }

    private static RealityCheckSession activeSession() {
        RealityCheckSession session = new RealityCheckSession();
        session.setPlayerId(1001);
        session.setFranchiseId(10);
        session.setStatus("ACTIVE");
        session.setIntervalMinutes(10);
        session.setStartedAt(1_000);
        session.setLastPromptAt(1_000);
        session.setElapsedSeconds(0);
        session.setNetAmountMinor(0);
        session.setNextCheckAt(1_600);
        return session;
    }
}
