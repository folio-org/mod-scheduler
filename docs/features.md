# Module Features

This module provides the following features:

| Feature | Description |
|---------|-------------|
| [Scheduled Timer Delivery](features/scheduled-timer-delivery.md) | Delivers scheduled timer requests with safe transient-failure retries and per-timer execution serialization. |
| [Timer Event Confirmation](features/timer-event-confirmation.md) | Publishes a `ResourceResultEvent` to Kafka after each scheduled-job event is processed, enabling `mgr-tenant-entitlements` to advance entitlement stage tracking. |
