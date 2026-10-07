package com.faber.core.config.scheduler;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps database-backed scheduled work idle until startup runners, including database initialization, finish.
 */
@Component
public class SchedulerStartupGate {

    private volatile boolean ready;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        ready = true;
    }

    public boolean isReady() {
        return ready;
    }
}
