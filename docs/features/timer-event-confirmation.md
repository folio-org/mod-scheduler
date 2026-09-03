---
feature_id: timer-event-confirmation
title: Timer Event Confirmation
updated: 2026-09-02
---

# Timer Event Confirmation

## What it does
After processing a scheduled-job Kafka event (CREATE, UPDATE, or DELETE), mod-scheduler
publishes a `ResourceResultEvent` to a configurable confirmation topic. On success the
event is sent after the database transaction commits; on failure — when all retries are
exhausted — it is sent immediately with the exception message in the `details` field.

## Why it exists
`mgr-tenant-entitlements` waits for this confirmation before advancing an entitlement
stage out of `IN_PROGRESS`. Without it the stage would wait indefinitely.

## Entry point(s)
| Type | Topic pattern | Description |
|------|---------------|-------------|
| Kafka consumer | `{application.environment}.*.mgr-tenant-entitlements.scheduled-job` | Incoming scheduled-job events that trigger confirmation publishing. |
| Kafka producer | `{application.environment}.mgr-tenant-entitlements.resource-result` | Outgoing `ResourceResultEvent` confirmations (configurable via `EVENT_CONFIRMATION_TOPIC`). |

## Business rules and constraints
- Only scheduled-job events (CREATE, UPDATE, DELETE) produce confirmations. Entitlement
  events (ENTITLE, REVOKE, UPGRADE) do not.
- A SUCCESS confirmation is published via a `@TransactionalEventListener(AFTER_COMMIT)`:
  it is sent only after the database transaction commits, so a rollback never produces a
  false SUCCESS.
- A FAILURE confirmation is published via a plain `@EventListener` when retries are
  exhausted, carrying the exception message in the `details` field.
- An unsupported operation type in a scheduled-job event also produces a FAILURE
  confirmation.
- Confirmation is sent asynchronously on a dedicated thread pool (`AsyncEvt-` prefix) and
  does not block the Kafka consumer thread.
- The `moduleId` in the confirmation is extracted from `newValue` for CREATE and UPDATE
  events, and from `oldValue` for DELETE events.

## Error behavior
- If the Kafka send itself fails, the error is logged at ERROR level with the event ID,
  tenant, module ID, and status. The original Kafka record is not reprocessed.
- When the feature is disabled, all confirmation events are silently dropped and
  logged at DEBUG level only.

## Configuration
| Variable | Purpose |
|----------|---------|
| `EVENT_CONFIRMATION_ENABLED` | Enables confirmation publishing; default `false`. When `false`, a no-op sender is wired and no messages are produced. |
| `EVENT_CONFIRMATION_TOPIC` | Kafka topic for outgoing confirmations; default `{application.environment}.mgr-tenant-entitlements.resource-result`. |

## Dependencies and interactions
Confirmations are consumed by `mgr-tenant-entitlements` to advance entitlement stage
tracking. The feature shares the existing `KafkaTemplate` producer configuration and
requires no additional Kafka credentials beyond what the module already uses.
