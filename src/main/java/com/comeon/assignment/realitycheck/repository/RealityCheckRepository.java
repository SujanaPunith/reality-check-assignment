package com.comeon.assignment.realitycheck.repository;

import com.comeon.assignment.realitycheck.model.PlayerRecord;
import com.comeon.assignment.realitycheck.model.RealityCheckSession;
import lombok.RequiredArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RealityCheckRepository {

    private final Jdbi jdbi;

    public Optional<RealityCheckSession> findByPlayerAndStatus(long playerId, String status) {
        try (Handle handle = jdbi.open()) {
            return handle.createQuery("SELECT * FROM reality_check_session WHERE player_id = :playerId AND status = :status")
                    .bind("playerId", playerId)
                    .bind("status", status)
                    .map((rs, ctx) -> {
                        RealityCheckSession s = new RealityCheckSession();
                        s.setId(rs.getLong("id"));
                        s.setPlayerId(rs.getLong("player_id"));
                        s.setFranchiseId(rs.getLong("franchise_id"));
                        s.setStatus(rs.getString("status"));
                        s.setIntervalMinutes(rs.getInt("interval_minutes"));
                        s.setStartedAt(rs.getLong("started_at"));
                        s.setLastPromptAt(rs.getLong("last_prompt_at"));
                        s.setElapsedSeconds(rs.getLong("elapsed_seconds"));
                        s.setNetAmountMinor(rs.getLong("net_amount_minor"));
                        s.setNextCheckAt(rs.getLong("next_check_at"));
                        long acknowledgedAt = rs.getLong("acknowledged_at");
                        if (!rs.wasNull()) {
                            s.setAcknowledgedAt(acknowledgedAt);
                        }
                        return s;
                    })
                    .findOne();
        }
    }

    public List<Long> findActivePlayerIds() {
        try (Handle handle = jdbi.open()) {
            return handle.createQuery("SELECT player_id FROM reality_check_session WHERE status = 'ACTIVE'")
                    .mapTo(Long.class)
                    .list();
        }
    }

    public boolean insertSession(RealityCheckSession s) {
        try (Handle handle = jdbi.open()) {
            handle.createUpdate("INSERT INTO reality_check_session " +
                            "(player_id, franchise_id, status, interval_minutes, started_at, last_prompt_at, " +
                            " elapsed_seconds, net_amount_minor, next_check_at, acknowledged_at) " +
                            "VALUES (:playerId, :franchiseId, :status, :intervalMinutes, :startedAt, :lastPromptAt, " +
                            " :elapsedSeconds, :netAmountMinor, :nextCheckAt, :acknowledgedAt)")
                    .bind("playerId", s.getPlayerId())
                    .bind("franchiseId", s.getFranchiseId())
                    .bind("status", s.getStatus())
                    .bind("intervalMinutes", s.getIntervalMinutes())
                    .bind("startedAt", s.getStartedAt())
                    .bind("lastPromptAt", s.getLastPromptAt())
                    .bind("elapsedSeconds", s.getElapsedSeconds())
                    .bind("netAmountMinor", s.getNetAmountMinor())
                    .bind("nextCheckAt", s.getNextCheckAt())
                    .bind("acknowledgedAt", s.getAcknowledgedAt())
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .findOne()
                    .ifPresent(s::setId);
            return true;
        } catch (UnableToExecuteStatementException exception) {
            if (isActiveSessionUniqueViolation(exception)) {
                return false;
            }
            throw exception;
        }
    }

    private boolean isActiveSessionUniqueViolation(Throwable throwable) {
        if (hasSqlState(throwable, "23505")) {
            return true;
        }
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && "23000".equals(sqlException.getSQLState())
                    && sqlException.getErrorCode() == 1062
                    && sqlException.getMessage().contains("uq_reality_check_session_active_player")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSqlState(Throwable throwable, String sqlState) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlState.equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    public boolean updateInterval(RealityCheckSession session) {
        try (Handle handle = jdbi.open()) {
            return handle.createUpdate("""
                    UPDATE reality_check_session
                    SET interval_minutes = :intervalMinutes,
                        elapsed_seconds = :elapsedSeconds,
                        next_check_at = :nextCheckAt
                    WHERE id = :id
                      AND status = 'ACTIVE'
                    """)
                    .bind("intervalMinutes", session.getIntervalMinutes())
                    .bind("elapsedSeconds", session.getElapsedSeconds())
                    .bind("nextCheckAt", session.getNextCheckAt())
                    .bind("id", session.getId())
                    .execute() == 1;
        }
    }

    public boolean stopSession(RealityCheckSession session) {
        try (Handle handle = jdbi.open()) {
            return handle.createUpdate("""
                    UPDATE reality_check_session
                    SET status = 'STOPPED',
                        elapsed_seconds = :elapsedSeconds
                    WHERE id = :id
                      AND status = 'ACTIVE'
                    """)
                    .bind("elapsedSeconds", session.getElapsedSeconds())
                    .bind("id", session.getId())
                    .execute() == 1;
        }
    }

    public boolean recordAcknowledgement(RealityCheckSession session) {
        return jdbi.inTransaction(handle -> {
            if (!updateAcknowledgedAt(handle, session)) {
                return false;
            }

            insertAcknowledgement(
                    handle,
                    session.getId(),
                    session.getPlayerId(),
                    session.getAcknowledgedAt());
            return true;
        });
    }

    public boolean claimDueSession(RealityCheckSession session, long now) {
        long elapsedSeconds = now - session.getStartedAt();
        long nextCheckAt = now + (long) session.getIntervalMinutes() * 60;

        try (Handle handle = jdbi.open()) {
            boolean claimed = handle.createUpdate("""
                    UPDATE reality_check_session
                    SET elapsed_seconds = :elapsedSeconds,
                        last_prompt_at = :lastPromptAt,
                        next_check_at = :nextCheckAt
                    WHERE id = :id
                      AND status = 'ACTIVE'
                      AND next_check_at <= :now
                      AND interval_minutes = :intervalMinutes
                    """)
                    .bind("elapsedSeconds", elapsedSeconds)
                    .bind("lastPromptAt", now)
                    .bind("nextCheckAt", nextCheckAt)
                    .bind("id", session.getId())
                    .bind("now", now)
                    .bind("intervalMinutes", session.getIntervalMinutes())
                    .execute() == 1;

            if (claimed) {
                session.setElapsedSeconds(elapsedSeconds);
                session.setLastPromptAt(now);
                session.setNextCheckAt(nextCheckAt);
            }
            return claimed;
        }
    }

    public void updateElapsedSecondsForNonDueActiveSession(long sessionId, long now) {
        try (Handle handle = jdbi.open()) {
            handle.createUpdate("""
                    UPDATE reality_check_session
                    SET elapsed_seconds = :now - started_at
                    WHERE id = :id
                      AND status = 'ACTIVE'
                      AND next_check_at > :now
                    """)
                    .bind("id", sessionId)
                    .bind("now", now)
                    .execute();
        }
    }

    private boolean updateAcknowledgedAt(Handle handle, RealityCheckSession session) {
        return handle.createUpdate("""
            UPDATE reality_check_session
            SET acknowledged_at = CASE
                    WHEN acknowledged_at IS NULL OR acknowledged_at < :acknowledgedAt
                    THEN :acknowledgedAt
                    ELSE acknowledged_at
                END
            WHERE id = :id
              AND status = 'ACTIVE'
            """)
                .bind("acknowledgedAt", session.getAcknowledgedAt())
                .bind("id", session.getId())
                .execute() == 1;
    }

    private void insertAcknowledgement(
            Handle handle,
            long sessionId,
            long playerId,
            long acknowledgedAt) {
        handle.createUpdate("""
                INSERT INTO reality_check_acknowledgement (session_id, player_id, acknowledged_at)
                VALUES (:sessionId, :playerId, :acknowledgedAt)
                """)
                .bind("sessionId", sessionId)
                .bind("playerId", playerId)
                .bind("acknowledgedAt", acknowledgedAt)
                .execute();
    }


    public PlayerRecord findPlayerFull(long playerId) {
        try (Handle handle = jdbi.open()) {
            return handle.createQuery("SELECT * FROM player WHERE id = :playerId")
                    .bind("playerId", playerId)
                    .map((rs, ctx) -> {
                        PlayerRecord p = new PlayerRecord();
                        p.id = rs.getLong("id");
                        p.franchiseId = rs.getLong("franchise_id");
                        p.username = rs.getString("username");
                        p.email = rs.getString("email");
                        p.firstName = rs.getString("first_name");
                        p.lastName = rs.getString("last_name");
                        p.gender = rs.getString("gender");
                        p.birthDate = rs.getDate("birth_date") == null ? null : rs.getDate("birth_date").toLocalDate();
                        p.country = rs.getString("country");
                        p.city = rs.getString("city");
                        p.address = rs.getString("address");
                        p.postalCode = rs.getString("postal_code");
                        p.phone = rs.getString("phone");
                        p.currency = rs.getString("currency");
                        p.language = rs.getString("language");
                        p.timezone = rs.getString("timezone");
                        p.registeredAt = rs.getString("registered_at");
                        p.lastLoginAt = rs.getString("last_login_at");
                        p.kycStatus = rs.getString("kyc_status");
                        p.vipLevel = rs.getInt("vip_level");
                        p.marketingOptIn = rs.getBoolean("marketing_opt_in");
                        p.selfExcluded = rs.getBoolean("self_excluded");
                        p.depositLimitMinor = rs.getLong("deposit_limit_minor");
                        p.balanceMinor = rs.getLong("balance_minor");
                        p.bonusBalanceMinor = rs.getLong("bonus_balance_minor");
                        p.loyaltyPoints = rs.getLong("loyalty_points");
                        p.affiliateId = rs.getString("affiliate_id");
                        p.referralCode = rs.getString("referral_code");
                        p.riskScore = rs.getInt("risk_score");
                        p.accountStatus = rs.getString("account_status");
                        p.createdAt = rs.getString("created_at");
                        p.updatedAt = rs.getString("updated_at");
                        return p;
                    })
                    .findOne()
                    .orElse(null);
        }
    }
}
