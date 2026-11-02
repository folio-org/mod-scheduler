# mod-scheduler

FOLIO module that runs recurring HTTP calls ("timers") per tenant on a clustered Quartz JDBC job store. SYSTEM timers come from module descriptors through Kafka; USER timers come from the `/scheduler/timers` REST API.

## Commands

```bash
mvn clean package -DskipTests  # build
mvn test                       # unit tests (@UnitTest, *Test.java)
mvn verify                     # unit and integration tests (@IntegrationTest, *IT.java); needs Docker
mvn test -Dtest=SchedulerTenantServiceTest#afterTenantUpdate_positive  # one unit test
mvn verify -Dit.test=SchedulerTimerIT#getById_positive -Dtest=SkipSurefire -Dsurefire.failIfNoSpecifiedTests=false  # one IT, unit tests skipped
mvn verify -Pcoverage          # JaCoCo report; the 80% check does not fail the build
mvn checkstyle:check           # style only
```

Surefire runs only the `unit` tag and failsafe only the `integration` tag, so a test class without `@UnitTest` or `@IntegrationTest` never runs. Integration tests start PostgreSQL, Kafka, and Keycloak with Testcontainers (`BaseIntegrationTest`, `@EnableKeycloakTlsMode`) and stub module calls with WireMock.

Checkstyle also runs at `process-classes`, so every build above fails on a violation, test sources included. The base rules come from `folio-java-checkstyle`; this repository limits methods to 21 lines (`checkstyle/checkstyle-checker.properties`), and the limit does not apply to test sources.

Unit test guide: https://github.com/folio-org/folio-eureka-ai-dev/blob/master/docs/testing/unit-testing.md

## Conventions

- The OpenAPI spec `src/main/resources/swagger.api/mod-scheduler.yaml` generates `org.folio.scheduler.domain.dto` and `org.folio.scheduler.rest.resource`. Change the spec, not the generated sources.
- There are two Liquibase changelogs. `changelog-quartz.xml` creates the shared Quartz tables in `sys_quartz_mod_scheduler` once, at application startup (`QuartzSchemaInitializer`). `changelog-master.xml` creates the `timer` table in each tenant schema at tenant init. Put a change in the changelog that owns the table.
- Timer HTTP calls retry only the failures listed in `TimerExecutionRetryClassifier` (connection refused, connect timeout, connection pool timeout, 5xx with error code `authorization_error`). Keep it an allowlist, because most timer endpoints are `POST` and some charge fees or send patron notices. For the same reason, `HttpClientConfiguration` disables the automatic retries of Apache HttpClient.
- The REST API rejects changes to SYSTEM timers unless `SCHEDULER_API_ALLOW_SYSTEM_TIMER_MUTATION=true`. A USER timer takes its `userId` from the request context and stores it in its own column, not in the `timer_descriptor` JSONB.

## Gotchas

- Retry settings live under `application.retry.config` and serve different paths. `system-user` and `user-impersonation` are Spring `@Retryable` with exponential backoff. `timer-execution` is the `RetryTemplate` around the timer HTTP call; its attempt count includes the first call. `scheduled-timer-event` and `entitlement-event` are a fixed Kafka backoff (`KafkaConfiguration`).
- The Kafka backoff retries only two errors: a running Liquibase migration and a missing tenant table ("relation ... does not exist"). By default both retry without limit. Any other error is not retried and the record is skipped: a `scheduled-job` failure publishes a FAILURE confirmation only when `EVENT_CONFIRMATION_ENABLED=true`, and an `entitlement` failure is only logged.
- `KafkaEventService` and the write methods of `SchedulerTimerService` are transactional, and Quartz scheduling joins the same database transaction. A failure rolls back both the timer rows and the Quartz jobs.
- The module never creates the system user. When a SYSTEM timer runs, `SystemUserService` looks up `{tenantId}-system-user` in Keycloak and retries `NotFoundException`; `SYSTEM_USER_RETRY_ATTEMPTS` defaults to `Integer.MAX_VALUE`. A missing user, or a user without the `user_id` attribute, keeps a Quartz worker thread busy. The pool (`QUARTZ_POOL_THREAD_COUNT`, default 5) is shared by all tenants.
- A failed timer call is logged as `timer.execution.failure` and dropped. The trigger keeps its schedule, and no failure state is stored. Errors before the call (impersonation, missing `userId`) go to Quartz, which logs them.
- A routing entry has zero or one HTTP method (`maxItems: 1`); with none, the timer calls `POST`. The schema text still says `*` means all methods, but the executor supports only `GET`, `POST`, `PUT`, and `DELETE`. Any other value logs `UNSUPPORTED_METHOD` and makes no call.
- `SchedulerTimerService.create` is an upsert by natural key `{type}#{moduleName}#{method}#{path}`, built with the lowercase type (`system#...`). Rows rewritten by changeset `07` keep an uppercase `SYSTEM#`/`USER#` prefix until their next update. The lookup is case-sensitive, so a create does not match such a row and adds a second timer.
- Entitlement events switch timers by module name, not module id. `ENTITLE` and `UPGRADE` enable and schedule the module's disabled timers, except USER timers without `userId`, which stay disabled. `REVOKE` disables the timers and deletes their Quartz jobs but keeps the rows. If the tenant `timer` table does not exist, the event is ignored.
- Tenant init (`SchedulerTenantService.afterTenantUpdate`) writes the tenant module-version marker and restarts all Kafka listener containers, for every tenant. With `KAFKA_TENANT_FILTER_ENABLED=true`, events for a tenant without the marker or with another module version are skipped, not retried. Consumer group ids include the module version, so each deployed version consumes every event.
