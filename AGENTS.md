# mod-scheduler

Spring Boot microservice (Java 21) providing scheduled job execution for FOLIO. Quartz Scheduler for jobs, Kafka for event-driven provisioning, Keycloak for auth, PostgreSQL/Liquibase.

## Build & Test

```bash
mvn clean install              # full build (unit, then integration tests)
mvn clean install -DskipTests=true  # skip tests
mvn test                       # unit tests (*Test.java, @UnitTest)
mvn verify                     # unit + integration tests (*IT.java, @IntegrationTest)
mvn test -Dtest=SchedulerTenantServiceTest#afterTenantUpdate_positive   # single unit test
mvn verify -Dit.test=SchedulerTimerIT#getById_positive -Dtest=SkipSurefire -Dsurefire.failIfNoSpecifiedTests=false   # single IT, skips unit tests
mvn clean install -Pcoverage   # JaCoCo report; the 80% rule does not fail the build
mvn checkstyle:check           # auto-runs during install
```

Run locally needs PostgreSQL + env (`DB_*`, `okapi.url`); Docker build `docker build -t mod-scheduler .`. See `README.md` for full env vars.

Unit-testing guide: https://github.com/folio-org/folio-eureka-ai-dev/blob/master/docs/testing/unit-testing.md

## Style

Match the repository's existing style: use maintained implementations and tests as templates for new code, and keep the structure, naming, and conventions of neighboring files.

## Pitfalls

- Retry mechanisms differ: the system-user lookup uses Spring Retry with exponential backoff (`SYSTEM_USER_RETRY_*`); Kafka timer events use a fixed backoff (`KafkaConfiguration`). Do not tune the wrong one.
- System user is never created here: when a SYSTEM timer runs, `SystemUserService` looks up `{tenantId}-system-user` in Keycloak (with retry on `NotFoundException`). If the user is missing, the problem is outside this module.
- Routing entries carry at most one HTTP method (`maxItems: 1`, optional): with no method, execution defaults to `POST`. Do not add validation that requires a method.
- Natural-key dedup: `{type}#{moduleName}#{methods}#{path}`: the runtime lowercases the type; legacy migration-`07` rows use an uppercase `SYSTEM#…` prefix, a known case mismatch vs the case-sensitive `natural_key_index` (see MODSCHED-88).
- Timer triggers are delay-based or schedule-based (cron in Unix or Quartz format; Unix is auto-converted via `CronUtils`), never both; timer types are SYSTEM (from module descriptors via Kafka) or USER (via REST).
- Do not edit `org.folio.scheduler.domain.dto` or `.rest.resource`; both are generated from the OpenAPI spec at every build.
- Liquibase (`changelog-master.xml`, `changelog-quartz.xml`, `changes/*.xml`): add new changesets, never modify existing ones.

## Runtime notes

- Multi-tenancy: tenant-specific schemas (`tenant_modulename.timer`), Quartz tables in `sys_quartz_mod_scheduler`, lifecycle via `SchedulerTenantService`.
- Kafka: `scheduled-job` topic (module descriptor CREATE/UPDATE/DELETE → SYSTEM timers from routing entries) and `entitlement` topic (tenant enable/revoke → enable/disable + reschedule).
- Secure store (`SECRET_STORE_TYPE`): Vault (default) / AWS-SSM / FSSP: OAuth client secrets and Keycloak admin credentials.
- JSONB: timer descriptors are stored as JSONB and queried with native PG JSONB operators; timers execute HTTP calls with tenant/user context (`OkapiClient`).

## Testing

- Unit (`*Test.java`, `@UnitTest`): Mockito.
- Integration (`*IT.java`, `@IntegrationTest`): Testcontainers (Postgres, Kafka), WireMock, Keycloak realms; extend `BaseIntegrationTest`. Annotations: `@WireMockStub`, `@KeycloakRealms`, `@Sql`.
