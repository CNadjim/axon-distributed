package io.github.cnadjim.axon.distributed.deadline;

import org.axonframework.deadline.DeadlineManager;
import org.axonframework.messaging.ScopeDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CombinedDeadlineManagerRoutingTest {

    private DeadlineManager transientDeadlineManager;
    private DeadlineManager persistentDeadlineManager;
    private CombinedDeadlineManager combinedDeadlineManager;

    @BeforeEach
    void setUp() {
        transientDeadlineManager = mock(DeadlineManager.class);
        persistentDeadlineManager = mock(DeadlineManager.class);
        combinedDeadlineManager = new CombinedDeadlineManager(transientDeadlineManager, persistentDeadlineManager);
    }

    @Test
    void shouldRouteShortDeadlineToTransientManager() {
        ScopeDescriptor scope = mock(ScopeDescriptor.class);
        Instant shortDeadline = Instant.now().plusSeconds(60);

        when(transientDeadlineManager.schedule(any(Instant.class), eq("deadline-short"), eq("payload"), eq(scope)))
                .thenReturn("transient-id");

        String scheduleId = combinedDeadlineManager.schedule(shortDeadline, "deadline-short", "payload", scope);

        assertThat(scheduleId).isEqualTo("transient-id");
        verify(transientDeadlineManager).schedule(shortDeadline, "deadline-short", "payload", scope);
        verifyNoInteractions(persistentDeadlineManager);
    }

    @Test
    void shouldRouteLongDeadlineToPersistentManager() {
        ScopeDescriptor scope = mock(ScopeDescriptor.class);
        Instant longDeadline = Instant.now().plusSeconds(360);

        when(persistentDeadlineManager.schedule(any(Instant.class), eq("deadline-long"), eq("payload"), eq(scope)))
                .thenReturn("persistent-id");

        String scheduleId = combinedDeadlineManager.schedule(longDeadline, "deadline-long", "payload", scope);

        assertThat(scheduleId).isEqualTo("persistent-id");
        verify(persistentDeadlineManager).schedule(longDeadline, "deadline-long", "payload", scope);
        verifyNoInteractions(transientDeadlineManager);
    }

    @Test
    void shouldCancelOnBothManagers() {
        combinedDeadlineManager.cancelSchedule("deadline", "schedule-id");

        verify(transientDeadlineManager).cancelSchedule("deadline", "schedule-id");
        verify(persistentDeadlineManager).cancelSchedule("deadline", "schedule-id");
    }
}

