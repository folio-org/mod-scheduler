package org.folio.scheduler.it;

import static java.util.Collections.singletonList;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerUnit;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.domain.model.TimerType;
import org.folio.scheduler.repository.SchedulerTimerRepository;
import org.folio.scheduler.support.TestConstants;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@EnableKeycloakTlsMode
@IntegrationTest
@Sql(scripts = "classpath:/sql/timer-repo-it.sql", executionPhase = BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class SchedulerTimerRepositoryIT extends BaseIntegrationTest {

  private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TEST_USER_A_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID TEST_USER_B_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final String TEST_MODULE_NAME = "test-module";
  private static final String TEST_PATH_PATTERN = "/test";

  @Autowired
  private SchedulerTimerRepository repository;
  @Autowired
  private FolioModuleMetadata folioModuleMetadata;

  @BeforeAll
  static void beforeAll() {
    setUpTenant();
  }

  @AfterAll
  static void afterAll(@Autowired Scheduler scheduler) throws Exception {
    removeTenant();
    deleteAllQuartzJobs(scheduler);
  }

  @Test
  void switchTimersByModuleNameAndType_shouldSetEnabledTrue_forTimersWithEnabledFalseOrNull() {
    var module = "mod-foo";
    var enabled = true;

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeaders())) {
      var timers = repository.findByModuleNameAndEnabledState(module, enabled);
      repository.switchTimersByIds(timers.stream().map(TimerDescriptorEntity::getId).toList(), enabled);

      assertThat(timers).hasSize(2);
      assertThat(repository.findAll())
        .hasSize(3)
        .allSatisfy(t -> assertThat(t.getTimerDescriptor().getEnabled()).isTrue());
    }
  }

  @Test
  void switchTimersByModuleNameAndType_shouldSetEnabledFalse_forTimersWithEnabledTrue() {
    var module = "mod-foo";
    var enabled = false;

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeaders())) {
      var timers = repository.findByModuleNameAndEnabledState(module, enabled);
      repository.switchTimersByIds(timers.stream().map(TimerDescriptorEntity::getId).toList(), enabled);

      assertThat(timers).hasSize(1);
      assertThat(repository.findAll())
        .hasSize(3)
        .allSatisfy(t -> {
          if (nonNull(t.getTimerDescriptor().getEnabled())) {
            assertThat(t.getTimerDescriptor().getEnabled()).isFalse();
          }
        });
    }
  }

  @Test
  void saveAndFlush_positive_populatesAllAuditFields() {
    var entity = createTimerEntity();
    var now = OffsetDateTime.now(ZoneOffset.UTC);

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeadersWithUser(TEST_USER_ID))) {
      var saved = repository.saveAndFlush(entity);

      assertThat(saved.getCreatedDate()).isCloseTo(now, within(1, ChronoUnit.MINUTES));
      assertThat(saved.getCreatedByUserId()).isEqualTo(TEST_USER_ID);
      assertThat(saved.getUpdatedDate()).isCloseTo(now, within(1, ChronoUnit.MINUTES));
      assertThat(saved.getUpdatedByUserId()).isEqualTo(TEST_USER_ID);
      assertThat(saved.getCreatedDate().getOffset()).isEqualTo(now.getOffset());
      assertThat(saved.getUpdatedDate().getOffset()).isEqualTo(now.getOffset());
    }
  }

  @Test
  void saveAndFlush_positive_refreshesOnlyUpdateFields() {
    var entity = createTimerEntity();

    OffsetDateTime originalCreatedDate;
    UUID originalCreatedByUserId;

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeadersWithUser(TEST_USER_A_ID))) {
      var created = repository.saveAndFlush(entity);
      originalCreatedDate = created.getCreatedDate();
      originalCreatedByUserId = created.getCreatedByUserId();

      created.getTimerDescriptor().setEnabled(false);
      var now = OffsetDateTime.now(ZoneOffset.UTC);

      try (var ignored2 = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeadersWithUser(TEST_USER_B_ID))) {
        var updated = repository.saveAndFlush(created);

        assertThat(updated.getCreatedDate()).isEqualTo(originalCreatedDate);
        assertThat(updated.getCreatedByUserId()).isEqualTo(originalCreatedByUserId);
        assertThat(updated.getUpdatedDate()).isCloseTo(now, within(1, ChronoUnit.MINUTES));
        assertThat(updated.getUpdatedByUserId()).isEqualTo(TEST_USER_B_ID);
      }
    }
  }

  @Test
  void saveAndFlush_positive_handlesNullUserContext() {
    var entity = createTimerEntity();

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeadersWithUser(null))) {
      var saved = repository.saveAndFlush(entity);

      assertThat(saved.getCreatedDate()).isNotNull();
      assertThat(saved.getUpdatedDate()).isNotNull();
      assertThat(saved.getCreatedByUserId()).isNull();
      assertThat(saved.getUpdatedByUserId()).isNull();
    }
  }

  private TimerDescriptorEntity createTimerEntity() {
    var entity = new TimerDescriptorEntity();
    entity.setId(UUID.randomUUID());
    entity.setType(TimerType.USER);
    entity.setModuleName(TEST_MODULE_NAME);

    var descriptor = new TimerDescriptor();
    descriptor.setId(entity.getId());
    descriptor.setEnabled(true);
    descriptor.setModuleName(TEST_MODULE_NAME);

    var routingEntry = new RoutingEntry();
    routingEntry.setPathPattern(TEST_PATH_PATTERN);
    routingEntry.setMethods(List.of("POST"));
    routingEntry.setDelay("10");
    routingEntry.setUnit(TimerUnit.SECOND);

    descriptor.setRoutingEntry(routingEntry);
    entity.setTimerDescriptor(descriptor);

    return entity;
  }

  private Map<String, Collection<String>> prepareContextHeaders() {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(TENANT, singletonList(TENANT_ID));
    headers.put(USER_ID, singletonList(TestConstants.USER_ID));
    return headers;
  }

  private Map<String, Collection<String>> prepareContextHeadersWithUser(UUID userId) {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(TENANT, singletonList(TENANT_ID));
    if (userId != null) {
      headers.put(USER_ID, singletonList(userId.toString()));
    }
    return headers;
  }
}
