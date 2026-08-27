package com.comeon.assignment.realitycheck.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches {@link RealityCheckEvent}s to players.
 *
 * <p>A stub for brevity.
 */
public interface RealityCheckEventSender {
    Logger LOGGER = LoggerFactory.getLogger(RealityCheckEventSender.class);

    default void send(RealityCheckEvent event) {
        LOGGER.info("Sending reality check event: {}", event);
    }
}
