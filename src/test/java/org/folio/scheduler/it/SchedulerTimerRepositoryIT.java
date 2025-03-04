package org.folio.scheduler.it;

import static java.util.Collections.singletonList;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
    var type = TimerType.USER;
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

  private Map<String, Collection<String>> prepareContextHeaders() {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(TENANT, singletonList(TENANT_ID));
    headers.put(USER_ID, singletonList(TestConstants.USER_ID));
    return headers;
  }
}
