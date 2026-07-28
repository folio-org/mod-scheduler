---
feature_id: scheduled-timer-delivery
title: Scheduled Timer Delivery
updated: 2026-07-27
---

# Scheduled Timer Delivery

## What it does
Configured timers invoke the selected module HTTP endpoint when their Quartz schedule fires. Delivery retries a limited set of transient failures with exponential backoff, and prevents concurrent executions of the same timer across the Quartz cluster.

## Why it exists
Scheduled work should recover from safe, short-lived availability failures without duplicating work that may already be in progress. Serializing each timer's executions protects downstream modules from overlapping requests for the same timer.

## Entry point(s)
| Type | Schedule | Description |
|------|----------|-------------|
| Scheduled Job | Timer delay or cron schedule | Invokes the timer routing entry's configured HTTP method and path. |

| Method | Path | Description |
|--------|------|-------------|
| POST | /scheduler/timers | Creates a timer that is scheduled for delivery. |
| PUT | /scheduler/timers/{id} | Updates a timer's delivery schedule or routing entry. |

## Business rules and constraints
- A delivery uses the timer routing entry's GET, POST, PUT, or DELETE method and its configured path.
- Only a 5xx response containing error code `authorization_error`, connection refused, connect timeout, and connection-pool timeout are retried.
- Generic 5xx responses, all 4xx responses, read timeouts, DNS failures, TLS failures, and other I/O failures are not retried.
- Retry attempts include the initial request; the default maximum is four attempts.
- Retries reuse the same prepared timer request context; the timer is not re-read and the user is not re-impersonated between attempts.
- Quartz blocks a subsequent fire of the same timer until its active execution completes, including when the timer runs on another cluster instance.

## Error behavior
- A retryable failure is attempted until the configured attempt limit is reached; the final failure is logged as `timer.execution.failure`.
- A non-retryable HTTP or transport failure is logged once as `timer.execution.failure` without another delivery attempt.
- Each retry emits a `timer.execution.retry` structured log event with its retry number and classified reason.

## Configuration
| Variable | Purpose |
|----------|---------|
| `TIMER_EXECUTION_RETRY_DELAY` | Initial delay before retrying a timer HTTP call; default `3s`. |
| `TIMER_EXECUTION_RETRY_MAX_DELAY` | Maximum delay between timer HTTP-call retries; default `10s`. |
| `TIMER_EXECUTION_RETRY_ATTEMPTS` | Maximum delivery attempts, including the initial request; default `4`. |
| `TIMER_EXECUTION_RETRY_MULTIPLIER` | Exponential backoff multiplier between delivery attempts; default `2`. |

## Dependencies and interactions
Timer delivery sends the configured HTTP request to the sidecar. The sidecar routes it to the selected module using module-to-module calls.
