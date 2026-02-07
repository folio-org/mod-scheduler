package org.folio.scheduler.it;

import static java.util.Collections.singletonList;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.folio.scheduler.support.TestConstants.MODULE_NAME;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.support.TestConstants.USER_ID_UUID;
import static org.folio.scheduler.support.TestValues.timerDescriptorEntity;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
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
import org.springframework.test.context.jdbc.SqlMergeMode;

@EnableKeycloakTlsMode
@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class SchedulerTimerRepositoryIT extends BaseIntegrationTest {

  private static final UUID TEST_USER_A_ID = UUID.fromString("4dc43911-b45f-4bb4-9895-45c88f90a253");
  private static final UUID TEST_USER_B_ID = UUID.fromString("5d07750b-22ce-4f42-864a-3e476e6992e8");

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
  @Sql(scripts = "classpath:/sql/timer-repo-it.sql", executionPhase = BEFORE_TEST_METHOD)
  void switchTimersByModuleNameAndType_shouldSetEnabledTrue_forTimersWithEnabledFalseOrNull() {
    var enabled = true;

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeaders())) {
      var timers = repository.findByModuleNameAndEnabledState(MODULE_NAME, enabled);
      repository.switchTimersByIds(timers.stream().map(TimerDescriptorEntity::getId).toList(), enabled);

      assertThat(timers).hasSize(2);
      assertThat(repository.findAll())
        .hasSize(3)
        .allSatisfy(t -> assertThat(t.getTimerDescriptor().getEnabled()).isTrue());
    }
  }

  @Test
  @Sql(scripts = "classpath:/sql/timer-repo-it.sql", executionPhase = BEFORE_TEST_METHOD)
  void switchTimersByModuleNameAndType_shouldSetEnabledFalse_forTimersWithEnabledTrue() {
    var enabled = false;

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeaders())) {
      var timers = repository.findByModuleNameAndEnabledState(MODULE_NAME, enabled);
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
    var entity = timerDescriptorEntity();
    var now = OffsetDateTime.now();

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata,
      prepareContextHeadersWithUser(USER_ID_UUID))) {
      var saved = repository.saveAndFlush(entity);

      assertThat(saved.getCreatedDate()).isCloseTo(now, within(1, ChronoUnit.MINUTES));
      assertThat(saved.getCreatedByUserId()).isEqualTo(USER_ID_UUID);
      assertThat(saved.getUpdatedDate()).isCloseTo(now, within(1, ChronoUnit.MINUTES));
      assertThat(saved.getUpdatedByUserId()).isEqualTo(USER_ID_UUID);
      assertThat(saved.getCreatedDate().getOffset()).isEqualTo(now.getOffset());
      assertThat(saved.getUpdatedDate().getOffset()).isEqualTo(now.getOffset());
    }
  }

  @Test
  void saveAndFlush_positive_refreshesOnlyUpdateFields() {
    var entity = timerDescriptorEntity();

    OffsetDateTime originalCreatedDate;
    UUID originalCreatedByUserId;

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata,
      prepareContextHeadersWithUser(TEST_USER_A_ID))) {
      var created = repository.saveAndFlush(entity);
      originalCreatedDate = created.getCreatedDate();
      originalCreatedByUserId = created.getCreatedByUserId();

      created.getTimerDescriptor().setEnabled(false);
      var now = OffsetDateTime.now(ZoneOffset.UTC);

      try (var ignored2 = new FolioExecutionContextSetter(folioModuleMetadata,
        prepareContextHeadersWithUser(TEST_USER_B_ID))) {
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
    var entity = timerDescriptorEntity();

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeadersWithUser(null))) {
      var saved = repository.saveAndFlush(entity);

      assertThat(saved.getCreatedDate()).isNotNull();
      assertThat(saved.getUpdatedDate()).isNotNull();
      assertThat(saved.getCreatedByUserId()).isNull();
      assertThat(saved.getUpdatedByUserId()).isNull();
    }
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
