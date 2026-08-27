package com.comeon.assignment.realitycheck.repository;

import com.comeon.assignment.realitycheck.model.PlayerRecord;
import com.comeon.assignment.realitycheck.model.RealityCheckSession;
import lombok.RequiredArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.Handle;
import org.springframework.stereotype.Repository;

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

    public void insertSession(RealityCheckSession s) {
        Handle handle = null;
        try {
            handle = jdbi.open();
            handle.createUpdate("INSERT INTO reality_check_session " +
                            "(player_id, franchise_id, status, interval_minutes, started_at, last_prompt_at, " +
                            " elapsed_seconds, net_amount_minor, next_check_at) " +
                            "VALUES (:playerId, :franchiseId, :status, :intervalMinutes, :startedAt, :lastPromptAt, " +
                            " :elapsedSeconds, :netAmountMinor, :nextCheckAt)")
                    .bind("playerId", s.getPlayerId())
                    .bind("franchiseId", s.getFranchiseId())
                    .bind("status", s.getStatus())
                    .bind("intervalMinutes", s.getIntervalMinutes())
                    .bind("startedAt", s.getStartedAt())
                    .bind("lastPromptAt", s.getLastPromptAt())
                    .bind("elapsedSeconds", s.getElapsedSeconds())
                    .bind("netAmountMinor", s.getNetAmountMinor())
                    .bind("nextCheckAt", s.getNextCheckAt())
                    .execute();
        } finally {
            handle.close();
        }
    }

    public void updateSession(RealityCheckSession s) {
        jdbi.open().createUpdate("UPDATE reality_check_session SET status = :status, interval_minutes = :intervalMinutes, " +
                        "last_prompt_at = :lastPromptAt, elapsed_seconds = :elapsedSeconds, " +
                        "net_amount_minor = :netAmountMinor, next_check_at = :nextCheckAt " +
                        "WHERE id = :id")
                .bind("status", s.getStatus())
                .bind("intervalMinutes", s.getIntervalMinutes())
                .bind("lastPromptAt", s.getLastPromptAt())
                .bind("elapsedSeconds", s.getElapsedSeconds())
                .bind("netAmountMinor", s.getNetAmountMinor())
                .bind("nextCheckAt", s.getNextCheckAt())
                .bind("id", s.getId())
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
