package com.axon.distributed.deadline;


import org.axonframework.deadline.AbstractDeadlineManager;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.messaging.ScopeDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import java.time.Duration;
import java.time.Instant;

public class CombinedDeadlineManager extends AbstractDeadlineManager {

    private static final Logger logger = LoggerFactory.getLogger(CombinedDeadlineManager.class);
    private static final long TRANSIENT_LIMIT = 300L;
    private final DeadlineManager transientDeadlineManager;
    private final DeadlineManager persistentDeadlineManager;

    public CombinedDeadlineManager(DeadlineManager transientDeadlineManager, DeadlineManager persistentDeadlineManager) {
        this.transientDeadlineManager = transientDeadlineManager;
        this.persistentDeadlineManager = persistentDeadlineManager;
    }

    @Override
    public String schedule(@NonNull Instant triggerDateTime, @NonNull String deadlineName, Object messageOrPayload, @NonNull ScopeDescriptor deadlineScope) {
        if (Instant.now().plusSeconds(TRANSIENT_LIMIT).isAfter(triggerDateTime)) {
            logger.debug("Scheduling deadline {} with duration {} on transientDeadlineManager", deadlineScope.scopeDescription(), Duration.between(Instant.now(), triggerDateTime));
            return transientDeadlineManager.schedule(triggerDateTime, deadlineName, messageOrPayload, deadlineScope);
        } else {
            logger.debug("Scheduling deadline {} with duration {} on persistentDeadlineManager", deadlineScope.scopeDescription(), Duration.between(Instant.now(), triggerDateTime));
            return persistentDeadlineManager.schedule(triggerDateTime, deadlineName, messageOrPayload, deadlineScope);
        }
    }

    @Override
    public void cancelSchedule(@NonNull String deadlineName, @NonNull String scheduleId) {
        transientDeadlineManager.cancelSchedule(deadlineName, scheduleId);
        persistentDeadlineManager.cancelSchedule(deadlineName, scheduleId);
    }

    @Override
    public void cancelAll(@NonNull String deadlineName) {
        transientDeadlineManager.cancelAll(deadlineName);
        persistentDeadlineManager.cancelAll(deadlineName);
    }

    @Override
    public void cancelAllWithinScope(@NonNull String deadlineName, @NonNull ScopeDescriptor scope) {
        transientDeadlineManager.cancelAllWithinScope(deadlineName, scope);
        persistentDeadlineManager.cancelAllWithinScope(deadlineName, scope);
    }
}