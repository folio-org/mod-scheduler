# AGENTS.md - Coding Agent Guidelines for mod-scheduler

## Project Overview

`mod-scheduler` is a Spring Boot 3.5.7 microservice (Java 21) that provides scheduled job execution capabilities for the FOLIO library platform. It uses Quartz Scheduler for job management, Kafka for event-driven provisioning, and Keycloak for authentication.

## Build and Test Commands

### Build
```bash
# Full build with tests
mvn clean install

# Build without tests
mvn clean install -DskipTests=true

# Build with coverage report (80% minimum)
mvn clean install -Pcoverage
```

### Tests
```bash
# Unit tests only (pattern: **/*Test.java, group: unit)
mvn test

# Integration tests only (pattern: **/*IT.java, group: integration)
mvn verify

# All tests
mvn clean verify
```

### Linting
```bash
# Checkstyle verification (runs automatically during mvn install)
mvn checkstyle:check
```

### Run Locally
```bash
# Requires PostgreSQL running (see README.md for docker setup)
java \
  -Dserver.port=8081 \
  -DDB_HOST=localhost \
  -DDB_PORT=5432 \
  -DDB_DATABASE=postgres \
  -DDB_USERNAME=postgres \
  -DDB_PASSWORD=mysecretpassword \
  -Dokapi.url=http://localhost:9130 \
  -jar target/mod-scheduler-*.jar
```

### Docker
```bash
# Build image
docker build -t mod-scheduler .

# Run container (requires PostgreSQL container linked)
docker run --name mod-scheduler \
  --link postgres:postgres \
  -e DB_HOST=postgres \
  -e okapi.url=http://okapi:9130 \
  -p 8081:8081 \
  -d mod-scheduler
```

## High-Level Architecture

### Multi-Tenant Design
- Uses FolioExecutionContext for tenant isolation
- Database schemas are tenant-specific (e.g., `tenant_modulename.timer`)
- Quartz tables are in separate schema: `sys_quartz_mod_scheduler`
- Tenant lifecycle managed by `SchedulerTenantService` (implements folio-spring-base TenantService)

### Core Components

**Controllers**: REST API endpoints implementing OpenAPI-generated interfaces
- `SchedulerTimerController` - Timer CRUD operations at `/scheduler/timers`

**Services**: Business logic layer
- `SchedulerTimerService` - Timer persistence and validation
- `JobSchedulingService` - Quartz job scheduling/rescheduling/deletion
- `SchedulerTenantService` - Tenant initialization and cleanup
- `UserImpersonationService` - Token generation for scheduled jobs via Keycloak

**Repository**: Data access
- `SchedulerTimerRepository` - Spring Data JPA with JSONB queries for timer descriptors

**Domain**:
- `TimerDescriptorEntity` - JPA entity with JSONB column for flexible storage
- DTOs are auto-generated from OpenAPI spec in `org.folio.scheduler.domain.dto`

### Quartz Integration

**Job Store**: JDBC-based, clustered mode for horizontal scaling
- Tables in `sys_quartz_mod_scheduler` schema
- Initialized via Liquibase (`changelog-quartz.xml`)
- Thread pool size: configurable via `QUARTZ_POOL_THREAD_COUNT` (default: 5)

**Job Execution Flow**:
1. Timer created → `SchedulerTimerService.create()`
2. Job scheduled in Quartz → `JobSchedulingService.schedule()`
3. Trigger fires → `OkapiHttpRequestExecutor.execute()`
4. HTTP request sent via `OkapiClient` with tenant/user context

**Trigger Types**:
- **Delay-based**: Simple repeating trigger (e.g., every 5 minutes)
- **Schedule-based**: Cron trigger with timezone support (Unix or Quartz format)

### Kafka Event Processing

**Event Types**:
- **Scheduled Job Events** (`scheduled-job` topic): Module descriptor changes (CREATE/UPDATE/DELETE)
  - Creates/updates/deletes SYSTEM timers based on module routing entries
- **Entitlement Events** (`entitlement` topic): Tenant enables/revokes modules
  - Enables/disables timers and reschedules Quartz jobs

**Listeners**: `KafkaMessageListener` with retry logic
- Error handler retries if tenant schema not yet initialized ("relation does not exist")
- Concurrent processing with configurable thread count (`KAFKA_JOB_CONCURRENCY`)

**Context Management**: `FolioExecutionContextSetter` injects tenant from event payload

### Keycloak Authentication

**Components**:
- `KeycloakUserService` - User lookup and caching
- `SystemUserService` - System user retrieval with retry logic
- `ClientSecretService` - OAuth client secrets from secure store (cache-backed)
- `KeycloakUserImpersonationService` - Token generation for job execution

**Token Flow**:
1. System user created during tenant initialization (`{tenantId}-system-user`)
2. Job execution retrieves token via `UserImpersonationService.impersonate(tenant, userId)`
3. Token cached with TTL (refresh 25s before expiry)

### Secure Storage

Supports multiple backends (configured via `SECRET_STORE_TYPE`):
- **Vault** (default) - Production use
- **AWS SSM** - AWS deployments
- **FSSP** - FOLIO Secure Store Proxy

Used for storing OAuth client secrets and Keycloak admin credentials.

### Database Schema

**Timer Table**:
```sql
CREATE TABLE timer (
  id UUID PRIMARY KEY,
  type timer_type,        -- ENUM: SYSTEM or USER
  module_name VARCHAR,    -- For quick lookup
  module_id VARCHAR,      -- Module version
  natural_key VARCHAR,    -- Deduplication: {TYPE}#{MODULE}#{METHOD}#{PATH}
  timer_descriptor JSONB  -- Full descriptor
);
```

**JSONB Storage**: Allows flexible schema evolution without migrations

**Liquibase**: Changelogs in `src/main/resources/changelog/`
- `changelog-master.xml` - Entry point
- `changelog-quartz.xml` - Quartz schema
- `changes/*.xml` - Timer schema evolution

### Testing Structure

**Unit Tests** (`**/*Test.java`):
- Mockito-based mocking
- Run with `mvn test`
- Tagged with `@Tag("unit")`
- **For comprehensive unit testing guidance**, see [FOLIO Unit Testing Guide](https://github.com/folio-org/folio-eureka-ai-dev/blob/master/doc/testing/UnitTesting.md)

**Integration Tests** (`**/*IT.java`):
- TestContainers (PostgreSQL, Kafka)
- WireMock for external HTTP calls
- Keycloak realm configuration
- Run with `mvn verify`
- Tagged with `@Tag("integration")`
- Base class: `BaseIntegrationTest` provides tenant setup, SQL execution, REST client helpers

**Custom Annotations**:
- `@IntegrationTest` - Configures Spring context for integration tests
- `@WireMockStub` - Mocks HTTP endpoints
- `@KeycloakRealms` - Pre-loads Keycloak test realms
- `@Sql` - Executes SQL scripts before/after tests

**Coverage**: 80% minimum instruction coverage (JaCoCo)

## Key Implementation Patterns

### Natural Key Deduplication
When creating timers, check for existing timer with same natural key:
```
Format: {TYPE}#{MODULE_NAME}#{METHODS}#{PATH}
Example: SYSTEM#mod-foo#POST#/timers
```

### JSONB Queries
Use native PostgreSQL JSONB operators for complex queries:
```java
@Query("SELECT t FROM TimerDescriptorEntity t WHERE t.moduleName = :moduleName "
  + "AND jsonb_extract_path_text(t.timerDescriptor, 'enabled') = :enabled")
```

### Retry Logic
System user retrieval and Kafka event processing use Spring Retry with exponential backoff:
- Configurable via `SYSTEM_USER_RETRY_*` and `SCHEDULED_TIMER_EVENT_*` environment variables

### Caching
Caffeine caches with TTL for:
- Keycloak user IDs (30 minutes)
- System user IDs (30 minutes)
- Client secrets (6000 seconds)
- Tokens (refresh 25s before expiry)

### MapStruct Mappers
Use MapStruct for DTO ↔ Entity conversions:
- `TimerDescriptorMapper` - Converts between TimerDescriptor (DTO) and TimerDescriptorEntity

### Global Exception Handling
`ApiExceptionHandler` with `@RestControllerAdvice` handles all exceptions and returns proper HTTP status codes

## Code Generation

### OpenAPI
- Spec: `src/main/resources/swagger.api/mod-scheduler.yaml`
- Generated code: `target/generated-sources`
- DTOs: `org.folio.scheduler.domain.dto`
- API interfaces: `org.folio.scheduler.rest.resource`

Regenerated on every build via `openapi-generator-maven-plugin`.

## Important Configuration

### Multi-Tenancy
- Tenant ID passed via `x-okapi-tenant` header
- Database connection uses tenant-specific schema
- Liquibase migrations run per-tenant

### Cron Format
Supports both Unix and Quartz cron formats:
- **Unix**: `<minute> <hour> <day-of-month> <month> <day-of-week>`
- **Quartz**: `<second> <minute> <hour> <day-of-month> <month> <day-of-week> [year]`

Unix format automatically converted to Quartz via `CronUtils.convertToQuartzFormat()`.

### Timer Types
- **SYSTEM**: Created automatically from module descriptors via Kafka events
- **USER**: Created manually via REST API

### Routing Entries
Timer descriptors reference routing entries from module descriptors:
- Must specify exactly one HTTP method (GET/POST/PUT/DELETE)
- Path pattern supports wildcards
- Can have delay-based or schedule-based triggers (not both)

## Common Development Tasks

### Adding a New Service
1. Create interface in `service/` package
2. Implement with `@Service` annotation
3. Use constructor injection via Lombok `@RequiredArgsConstructor`
4. Add unit tests in `src/test/java/.../service/`
5. Add integration tests if external dependencies involved

### Adding a New REST Endpoint
1. Update `src/main/resources/swagger.api/mod-scheduler.yaml`
2. Rebuild to regenerate API interface
3. Implement generated interface in controller
4. Add tests in `SchedulerTimerControllerTest` and `SchedulerTimerIT`

### Database Schema Changes
1. Create new changeset in `src/main/resources/changelog/changes/`
2. Include in `changelog-master.xml`
3. Test with `mvn clean verify`
4. Ensure backwards compatibility or coordinate with platform team

### Adding Kafka Event Handling
1. Define event model in `integration/kafka/model/`
2. Add listener method in `KafkaMessageListener`
3. Implement business logic in `KafkaEventService`
4. Add integration test extending `BaseIntegrationTest`

### Debugging Quartz Jobs
- Check job execution: `await().timeout(TEN_SECONDS).until(() -> scheduler.getTriggerKeys(anyJobGroup()), hasSize(1))`
- View job data: `scheduler.getJobDetail(jobKey).getJobDataMap()`
- Inspect database: `SELECT * FROM sys_quartz_mod_scheduler.qrtz_job_details`

## Dependencies of Note

- **folio-spring-support** (10.0.0-SNAPSHOT) - FOLIO framework with multi-tenancy, Liquibase integration
- **applications-poc-tools** (3.1.0-SNAPSHOT) - FOLIO security, backend common utilities
- **spring-boot-starter-quartz** - Job scheduling
- **spring-kafka** - Event processing
- **keycloak-admin-client** - Authentication
- **cron-utils** (9.2.1) - Cron expression conversion
- **hypersistence-utils-hibernate-63** - JSONB type handling
