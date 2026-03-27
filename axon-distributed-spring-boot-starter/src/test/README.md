# Deadline serialization test harness

This test folder validates two critical behaviors before adding full PostgreSQL recovery scenarios:

1. `CombinedDeadlineManager` routing logic (`< 5 min` transient, otherwise persistent)
2. Event-serializer round-trip compatibility on representative deadline payload types

## Current tests

- `com.axon.distributed.deadline.CombinedDeadlineManagerRoutingTest`
- `com.axon.distributed.deadline.EventSerializerRoundTripTest`

## Why this matters

`DbSchedulerDeadlineManager` uses Axon's event serializer. If payloads cannot round-trip safely, deadline execution may fail when tasks are read from the DB scheduler table.

These tests provide a fast safety net before introducing heavier Testcontainers-based PostgreSQL restart/recovery tests.

