# mod-scheduler

Java/Spring Boot microservice providing scheduled job execution for FOLIO. Quartz Scheduler for jobs, Kafka for event-driven provisioning, Keycloak for auth, PostgreSQL/Liquibase.

## Build & Test

```bash
mvn clean install              # full build
mvn clean install -DskipTests=true  # skip tests
mvn test                       # unit tests (*Test.java, @Tag("unit"))
mvn verify                     # integration tests (*IT.java, @Tag("integration")); needs Docker
mvn clean install -Pcoverage   # coverage (JaCoCo; the 80% check does not fail the build)
mvn checkstyle:check           # auto-runs during install
```

Run locally needs PostgreSQL + env (`DB_*`, `okapi.url`); Docker build `docker build -t mod-scheduler .`. See `README.md` for full env vars.

## Architecture

**Multi-tenancy**: `FolioExecutionContext` for isolation; tenant-specific schemas (`tenant_modulename.timer`); Quartz tables in `sys_quartz_mod_scheduler`; lifecycle via `SchedulerTenantService`.

**Quartz**: JDBC job store, clustered for horizontal scaling; schema via `changelog-quartz.xml`; pool `QUARTZ_POOL_THREAD_COUNT` (default 5). Flow: timer created → `SchedulerTimerService.create()` → `JobSchedulingService.schedule()` → trigger fires → `OkapiHttpRequestExecutor.execute()` → HTTP via `OkapiClient` with tenant/user context. Triggers: delay-based (simple repeat) or schedule-based (cron, Unix or Quartz format, timezone-aware).

**Kafka**:
- `scheduled-job` topic: module descriptor CREATE/UPDATE/DELETE → manages SYSTEM timers from routing entries.
- `entitlement` topic: tenant enable/revoke → enable/disable + reschedule jobs.
- `KafkaMessageListener` with retry (retries on uninitialized tenant schema "relation does not exist"); concurrency via `KAFKA_JOB_CONCURRENCY` / `KAFKA_ENTITLEMENT_CONCURRENCY`; tenant injected via `FolioExecutionContextSetter`.

**Keycloak**: `KeycloakUserService`, `SystemUserService` (retry), `ClientSecretService` (secure store, cached), `KeycloakUserImpersonationService`. System user `{tenantId}-system-user` is looked up in Keycloak, not created by this module; tokens cached (refresh 25s before expiry).

**Secure store** (`SECRET_STORE_TYPE`): Vault (default) / AWS-SSM / FSSP — OAuth client secrets and Keycloak admin creds.

## Key Patterns

- **Natural-key dedup**: `{type}#{moduleName}#{methods}#{path}` — runtime lowercases the type (e.g. `system#mod-foo#POST#/timers`); legacy migration-`07` rows use an uppercase `SYSTEM#…` prefix, a known case mismatch vs the case-sensitive `natural_key_index` (see MODSCHED-88).
- **Retry**: Spring Retry w/ exponential backoff (`SYSTEM_USER_RETRY_*`); Kafka events use a fixed backoff (`SCHEDULED_TIMER_EVENT_*`).
- **Caching**: Caffeine TTL — Keycloak/system-user IDs (30m), client secrets (6000s), tokens (refresh 25s before expiry).
- **Cron**: Unix (`m h dom mon dow`) auto-converted to Quartz (`s m h dom mon dow [year]`) via `CronUtils.convertToQuartz()`.
- **Timer types**: SYSTEM (from module descriptors via Kafka) / USER (via REST). Routing entries specify at most one HTTP method (`POST` when none); delay- or schedule-based trigger (not both).

## Codegen & DB

- OpenAPI spec `src/main/resources/swagger.api/mod-scheduler.yaml` → `org.folio.scheduler.domain.dto` + `.rest.resource` in `target/generated-sources` (regenerated each build).
- Liquibase: `changelog-master.xml`, `changelog-quartz.xml`, `changes/*.xml`. Timer table key columns: `type` (SYSTEM/USER), `module_name`, `module_id`, `natural_key`, `timer_descriptor` (JSONB).

## Testing

- Unit (`*Test.java`, `@Tag("unit")`): Mockito. Guide: https://github.com/folio-org/folio-eureka-ai-dev/blob/master/docs/testing/unit-testing.md
- Integration (`*IT.java`, `@Tag("integration")`): Testcontainers (Postgres, Kafka), WireMock, Keycloak realms; extend `BaseIntegrationTest`. Annotations: `@IntegrationTest`, `@WireMockStub`, `@KeycloakRealms`, `@Sql`.
