package com.comeon.assignment.realitycheck.job;

import com.comeon.assignment.realitycheck.event.RealityCheckEvent;
import com.comeon.assignment.realitycheck.event.RealityCheckEventSender;
import com.comeon.assignment.realitycheck.service.RealityCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class RealityCheckRefreshJob {
    private final RealityCheckService service;
    private final RealityCheckEventSender eventSender;

    @Scheduled(fixedDelay = 60000)
    public void run() {
        List<Long> ids = service.activePlayerIds();
        log.info("Refreshing {} active reality check sessions", ids.size());
        for (Long id : ids) {
            service.refresh(id)
                    .map(RealityCheckEvent::from)
                    .ifPresent(eventSender::send);
        }
    }
}
