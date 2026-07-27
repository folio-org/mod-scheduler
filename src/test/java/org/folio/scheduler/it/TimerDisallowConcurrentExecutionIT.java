package org.folio.scheduler.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.folio.scheduler.domain.dto.TimerUnit.SECOND;
import static org.folio.scheduler.support.TestConstants.MODULE_ID;
import static org.folio.scheduler.support.TestConstants.MODULE_NAME;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.hamcrest.Matchers.is;
import static org.quartz.JobKey.jobKey;
import static org.quartz.TriggerKey.triggerKey;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.UUID;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger.TriggerState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

/**
 * Verifies that {@code @DisallowConcurrentExecution} keeps a single timer from overlapping itself (WI-5).
 *
 * <p>The timer fires once per second but the endpoint takes three seconds to respond, so without the annotation the
 * next fire would start on a free Quartz worker while the previous one is still running. With it, the trigger for
 * the next fire is held in {@link TriggerState#BLOCKED} until the running execution completes - which is what this
 * test asserts directly, rather than relying on a timing-sensitive call count.</p>
 */
@EnableKeycloakTlsMode
@IntegrationTest
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class TimerDisallowConcurrentExecutionIT extends BaseIntegrationTest {

  private static final String JOB_GROUP = TENANT_ID + "#" + MODULE_NAME;

  @Autowired private Scheduler scheduler;

  @BeforeAll
  static void beforeAll() {
    setUpTenant();
  }

  @AfterAll
  static void afterAll() {
    removeTenant();
  }

  @AfterEach
  void tearDown() throws SchedulerException {
    scheduler.clear();
  }

  @Test
  @WireMockStub("/wiremock/stubs/slow-timer-endpoint.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void execute_positive_blocksOverlappingFireUntilRunningJobCompletes() throws Exception {
    var timerId = UUID.randomUUID();
    var timerDescriptor = new TimerDescriptor()
      .id(timerId)
      .enabled(true)
      .moduleId(MODULE_ID)
      .routingEntry(new RoutingEntry()
        .methods(List.of("POST"))
        .pathPattern("/test/slow-timer")
        .delay("1")
        .unit(SECOND));

    doPost("/scheduler/timers", timerDescriptor).andExpect(jsonPath("$.enabled", is(true)));

    // while the first (3s) execution is running, the next 1s fire is held rather than started concurrently
    await().atMost(TEN_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> assertThat(scheduler.getTriggerState(triggerKey(timerId.toString(), JOB_GROUP)))
        .isEqualTo(TriggerState.BLOCKED));

    var triggerKey = triggerKey(timerId.toString(), JOB_GROUP);
    var jobKey = jobKey(timerId.toString(), JOB_GROUP);
    scheduler.pauseTrigger(triggerKey);
    await().atMost(TEN_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> assertThat(scheduler.getCurrentlyExecutingJobs())
        .noneMatch(context -> context.getJobDetail().getKey().equals(jobKey)));
  }
}
