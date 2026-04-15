package org.folio.scheduler.it;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TWO_SECONDS;
import static org.folio.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.scheduler.domain.dto.TimerUnit.SECOND;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.utils.TestUtils.asJsonString;
import static org.folio.scheduler.utils.TestUtils.await;
import static org.hamcrest.Matchers.is;
import static org.quartz.impl.matchers.GroupMatcher.anyJobGroup;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.extern.log4j.Log4j2;
import org.folio.integration.kafka.model.ResourceEvent;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.integration.kafka.model.ScheduledTimers;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.spring.liquibase.LiquibaseMigrationLockService;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@Log4j2
@EnableKeycloakTlsMode
@IntegrationTest
@TestPropertySource(properties = {
  "application.kafka.consumer.filtering.tenant-filter.enabled=true",
  "application.retry.config.scheduled-timer-event.retry-delay=10ms"
})
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class KafkaMessageFilteringIT extends BaseIntegrationTest {

  private static final String SCHEDULED_TIMER_TOPIC = "it.test.mgr-tenant-entitlements.scheduled-job";
  private static final String MODULE_ID = "mod-foo-1.0.0";

  @MockitoBean private LiquibaseMigrationLockService liquibaseMigrationLockService;
  @Autowired private Scheduler scheduler;
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;

  @BeforeAll
  static void beforeAll(@Autowired KafkaAdmin kafkaAdmin) {
    createTopic(SCHEDULED_TIMER_TOPIC, kafkaAdmin);
    setUpTenant();
  }

  @AfterAll
  static void afterAll(@Autowired Scheduler scheduler) throws Exception {
    removeTenant();
    deleteAllQuartzJobs(scheduler);
    assertThat(scheduler.getJobKeys(anyJobGroup())).isEmpty();
  }

  @AfterEach
  void tearDown() throws Exception {
    for (JobKey jobKey : scheduler.getJobKeys(anyJobGroup())) {
      scheduler.deleteJob(jobKey);
    }
  }

  @Test
  @WireMockStub({
    "/wiremock/stubs/get-enabled-tenants-test.json",
    "/wiremock/stubs/timer-endpoint.json"
  })
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void shouldFilterMessageForDisabledTenant_andProcessMessageForEnabledTenant() {
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, asJsonString(resourceEvent("disabled-tenant")));
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, asJsonString(resourceEvent(TENANT_ID)));

    await().untilAsserted(() -> doGet("/scheduler/timers")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1))));

    await().atMost(TWO_SECONDS).pollDelay(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(BaseIntegrationTest::verifyTimerRequestCallsCount);
  }

  private static ResourceEvent<ScheduledTimers> resourceEvent(String tenant) {
    return ResourceEvent.<ScheduledTimers>builder()
      .resourceName("Scheduled Job")
      .tenant(tenant)
      .type(CREATE)
      .newValue(scheduledTimers())
      .build();
  }

  private static ScheduledTimers scheduledTimers() {
    return new ScheduledTimers()
      .moduleId(MODULE_ID)
      .applicationId("app-foo-1.0.0")
      .timers(asList(new RoutingEntry()
        .methods(asList("POST"))
        .pathPattern("/test")
        .delay("1")
        .unit(SECOND)));
  }
}
