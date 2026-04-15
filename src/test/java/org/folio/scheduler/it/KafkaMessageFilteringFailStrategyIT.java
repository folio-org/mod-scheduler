package org.folio.scheduler.it;

import static java.util.Collections.singletonList;
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
import static org.springframework.http.HttpMethod.GET;
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
import org.folio.test.extensions.impl.WireMockAdminClient.RequestCriteria;
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
  "application.kafka.consumer.filtering.tenant-filter.tenant-disabled-strategy=FAIL",
  "application.retry.config.scheduled-timer-event.retry-delay=100ms"
})
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class KafkaMessageFilteringFailStrategyIT extends BaseIntegrationTest {

  private static final String SCHEDULED_TIMER_TOPIC = "it.test.mgr-tenant-entitlements.scheduled-job";
  private static final String MODULE_ID = "mod-foo-1.0.0";

  private static final String ENTITLEMENT_STUB_DISABLED = """
    {
      "priority": 5,
      "request": {
        "method": "GET",
        "urlPathPattern": "/entitlements/modules/.*"
      },
      "response": {
        "status": 200,
        "headers": { "Content-Type": "application/json" },
        "jsonBody": ["other-tenant"]
      }
    }
    """;

  private static final String ENTITLEMENT_STUB_ENABLED = """
    {
      "priority": 1,
      "request": {
        "method": "GET",
        "urlPathPattern": "/entitlements/modules/.*"
      },
      "response": {
        "status": 200,
        "headers": { "Content-Type": "application/json" },
        "jsonBody": ["test"]
      }
    }
    """;

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
  @WireMockStub("/wiremock/stubs/timer-endpoint.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void shouldRetryMessage_untilTenantBecomesEnabled() {
    wmAdminClient.addStubMapping(ENTITLEMENT_STUB_DISABLED);

    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, asJsonString(resourceEvent()));

    // wait until the filter has retried at least 3 times (proves FAIL strategy causes retries)
    var entitlementRequestCriteria = RequestCriteria.builder()
      .urlPathPattern("/entitlements/modules/.*")
      .method(GET)
      .build();
    await().untilAsserted(() ->
      assertThat(wmAdminClient.requestCount(entitlementRequestCriteria)).isGreaterThanOrEqualTo(3));

    // switch stub to make the tenant appear enabled
    wmAdminClient.addStubMapping(ENTITLEMENT_STUB_ENABLED);

    // verify the message is eventually processed
    await().untilAsserted(() -> doGet("/scheduler/timers")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1))));

    await().atMost(TWO_SECONDS).pollDelay(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(BaseIntegrationTest::verifyTimerRequestCallsCount);
  }

  private static ResourceEvent<ScheduledTimers> resourceEvent() {
    return ResourceEvent.<ScheduledTimers>builder()
      .resourceName("Scheduled Job")
      .tenant(TENANT_ID)
      .type(CREATE)
      .newValue(scheduledTimers())
      .build();
  }

  private static ScheduledTimers scheduledTimers() {
    return new ScheduledTimers()
      .moduleId(MODULE_ID)
      .applicationId("app-foo-1.0.0")
      .timers(singletonList(new RoutingEntry()
        .methods(singletonList("POST"))
        .pathPattern("/test")
        .delay("1")
        .unit(SECOND)));
  }
}
