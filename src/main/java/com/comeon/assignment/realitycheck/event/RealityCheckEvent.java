package com.comeon.assignment.realitycheck.event;

import com.comeon.assignment.realitycheck.model.RealityCheckSession;

/**
 * Reminder emitted to a player when a new reality check becomes due: how long they have been
 * playing and their net win/loss so far.
 */
public record RealityCheckEvent(
        long playerId,
        long franchiseId,
        int intervalMinutes,
        long elapsedSeconds,
        long netAmountMinor,
        long promptedAt,
        long nextCheckAt) {

    public static RealityCheckEvent from(RealityCheckSession session) {
        return new RealityCheckEvent(
                session.getPlayerId(),
                session.getFranchiseId(),
                session.getIntervalMinutes(),
                session.getElapsedSeconds(),
                session.getNetAmountMinor(),
                session.getLastPromptAt(),
                session.getNextCheckAt());
    }
}
