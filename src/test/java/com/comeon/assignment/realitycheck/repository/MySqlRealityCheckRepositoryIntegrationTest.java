package com.comeon.assignment.realitycheck.repository;

import com.comeon.assignment.realitycheck.model.RealityCheckSession;
import liquibase.integration.spring.SpringLiquibase;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MYSQL_INTEGRATION_URL", matches = ".+")
class MySqlRealityCheckRepositoryIntegrationTest {

    private Jdbi jdbi;
    private RealityCheckRepository firstRepository;
    private RealityCheckRepository secondRepository;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("MYSQL_INTEGRATION_URL");
        String username = System.getenv().getOrDefault("MYSQL_INTEGRATION_USER", "realitycheck");
        String password = System.getenv().getOrDefault("MYSQL_INTEGRATION_PASSWORD", "realitycheck-local");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
        liquibase.afterPropertiesSet();

        jdbi = Jdbi.create(dataSource);
        firstRepository = new RealityCheckRepository(jdbi);
        secondRepository = new RealityCheckRepository(Jdbi.create(dataSource));
        cleanTestSessions();
    }

    @AfterEach
    void tearDown() {
        if (jdbi != null) {
            cleanTestSessions();
        }
    }

    @Test
    void coordinatesIndependentRepositoriesThroughMySql() throws Exception {
        assertGeneratedActivePlayerColumn();

        RealityCheckSession firstActive = session(9_001L, "ACTIVE", 1_600L, -4_200L);
        RealityCheckSession secondActive = session(9_001L, "ACTIVE", 1_600L, -4_200L);
        List<Boolean> inserts = runConcurrently(
                () -> firstRepository.insertSession(firstActive),
                () -> secondRepository.insertSession(secondActive));
        assertThat(inserts).containsExactlyInAnyOrder(true, false);

        assertThat(firstRepository.insertSession(session(9_001L, "STOPPED", 1_600L, -4_200L))).isTrue();
        assertThat(secondRepository.insertSession(session(9_001L, "STOPPED", 1_600L, -4_200L))).isTrue();

        RealityCheckSession due = session(9_002L, "ACTIVE", 1_600L, -4_200L);
        assertThat(firstRepository.insertSession(due)).isTrue();
        RealityCheckSession firstReplica = firstRepository.findByPlayerAndStatus(9_002L, "ACTIVE").orElseThrow();
        RealityCheckSession secondReplica = secondRepository.findByPlayerAndStatus(9_002L, "ACTIVE").orElseThrow();
        List<Boolean> claims = runConcurrently(
                () -> firstRepository.claimDueSession(firstReplica, 1_700L),
                () -> secondRepository.claimDueSession(secondReplica, 1_700L));
        assertThat(claims).containsExactlyInAnyOrder(true, false);

        RealityCheckSession acknowledged = firstRepository.findByPlayerAndStatus(9_002L, "ACTIVE").orElseThrow();
        acknowledged.setAcknowledgedAt(1_750L);
        assertThat(secondRepository.recordAcknowledgement(acknowledged)).isTrue();

        RealityCheckSession stored = firstRepository.findByPlayerAndStatus(9_002L, "ACTIVE").orElseThrow();
        assertThat(stored.getNextCheckAt()).isEqualTo(2_300L);
        assertThat(stored.getAcknowledgedAt()).isEqualTo(1_750L);
        assertThat(stored.getNetAmountMinor()).isEqualTo(-4_200L);
        assertThat(historyCount(stored.getId())).isOne();
    }

    private void assertGeneratedActivePlayerColumn() {
        String expression = jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT generation_expression
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'reality_check_session'
                          AND column_name = 'active_player_id'
                        """)
                .mapTo(String.class)
                .one());
        assertThat(expression).containsIgnoringCase("status").containsIgnoringCase("player_id");
    }

    private long historyCount(long sessionId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT COUNT(*) FROM reality_check_acknowledgement WHERE session_id = :sessionId
                        """)
                .bind("sessionId", sessionId)
                .mapTo(Long.class)
                .one());
    }

    private void cleanTestSessions() {
        jdbi.useTransaction(handle -> {
            handle.createUpdate("""
                    DELETE FROM reality_check_acknowledgement WHERE player_id IN (9001, 9002)
                    """).execute();
            handle.createUpdate("""
                    DELETE FROM reality_check_session WHERE player_id IN (9001, 9002)
                    """).execute();
        });
    }

    private List<Boolean> runConcurrently(
            java.util.concurrent.Callable<Boolean> first,
            java.util.concurrent.Callable<Boolean> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstResult = executor.submit(() -> run(first, ready, start));
            Future<Boolean> secondResult = executor.submit(() -> run(second, ready, start));
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean run(
            java.util.concurrent.Callable<Boolean> operation,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return operation.call();
    }

    private static RealityCheckSession session(
            long playerId,
            String status,
            long nextCheckAt,
            long netAmountMinor) {
        RealityCheckSession session = new RealityCheckSession();
        session.setPlayerId(playerId);
        session.setFranchiseId(10);
        session.setStatus(status);
        session.setIntervalMinutes(10);
        session.setStartedAt(1_000L);
        session.setLastPromptAt(1_000L);
        session.setElapsedSeconds(0L);
        session.setNetAmountMinor(netAmountMinor);
        session.setNextCheckAt(nextCheckAt);
        return session;
    }
}
