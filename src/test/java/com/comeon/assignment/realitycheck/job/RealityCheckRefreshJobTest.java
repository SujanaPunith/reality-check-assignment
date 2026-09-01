package com.comeon.assignment.realitycheck.job;

import com.comeon.assignment.realitycheck.event.RealityCheckEvent;
import com.comeon.assignment.realitycheck.event.RealityCheckEventSender;
import com.comeon.assignment.realitycheck.model.RealityCheckSession;
import com.comeon.assignment.realitycheck.service.RealityCheckService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealityCheckRefreshJobTest {

    @Test
    void emitsEventOnlyWhenRefreshReturnsASuccessfullyClaimedSession() {
        RealityCheckService service = mock(RealityCheckService.class);
        RealityCheckEventSender eventSender = mock(RealityCheckEventSender.class);
        RealityCheckRefreshJob job = new RealityCheckRefreshJob(service, eventSender);
        RealityCheckSession claimedSession = activeSession();
        when(service.activePlayerIds()).thenReturn(List.of(1001L, 1002L));
        when(service.refresh(1001L)).thenReturn(Optional.of(claimedSession));
        when(service.refresh(1002L)).thenReturn(Optional.empty());

        job.run();

        verify(eventSender).send(RealityCheckEvent.from(claimedSession));
        verify(eventSender, never()).send(RealityCheckEvent.from(sessionFor(1002L)));
    }

    @Test
    void preservesNonZeroNetAmountInEventForSuccessfullyClaimedReminder() {
        RealityCheckService service = mock(RealityCheckService.class);
        RealityCheckEventSender eventSender = mock(RealityCheckEventSender.class);
        RealityCheckRefreshJob job = new RealityCheckRefreshJob(service, eventSender);
        RealityCheckSession claimedSession = activeSession();
        claimedSession.setNetAmountMinor(-4_200L);
        when(service.activePlayerIds()).thenReturn(List.of(1001L));
        when(service.refresh(1001L)).thenReturn(Optional.of(claimedSession));

        job.run();

        ArgumentCaptor<RealityCheckEvent> eventCaptor = ArgumentCaptor.forClass(RealityCheckEvent.class);
        verify(eventSender).send(eventCaptor.capture());
        assertThat(eventCaptor.getValue().netAmountMinor()).isEqualTo(-4_200L);
    }

    private static RealityCheckSession activeSession() {
        return sessionFor(1001L);
    }

    private static RealityCheckSession sessionFor(long playerId) {
        RealityCheckSession session = new RealityCheckSession();
        session.setPlayerId(playerId);
        session.setFranchiseId(10);
        session.setIntervalMinutes(10);
        session.setElapsedSeconds(600);
        session.setLastPromptAt(1_600);
        session.setNextCheckAt(2_200);
        return session;
    }
}
