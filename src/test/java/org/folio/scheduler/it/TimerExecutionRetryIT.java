package org.folio.scheduler.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.folio.scheduler.domain.dto.TimerUnit.MINUTE;
import static org.folio.scheduler.support.TestConstants.MODULE_ID;
import static org.folio.scheduler.support.TestConstants.MODULE_NAME;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.hamcrest.Matchers.is;
import static org.quartz.JobKey.jobKey;
import static org.quartz.TriggerKey.triggerKey;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Duration;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

/**
 * Covers the timer-execution retry policy end to end (MODSCHED-70, AC1-AC4).
 *
 * <p>Every timer here repeats once per minute but fires immediately, so exactly one execution happens inside the
 * assertion window and the wire-call counts below are exact rather than lower bounds. The configured attempt cap
 * for integration tests is in {@code application-it.yml}.</p>
 */
@EnableKeycloakTlsMode
@IntegrationTest
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class TimerExecutionRetryIT extends BaseIntegrationTest {

  /**
   * The {@code retry-attempts} value configured in {@code application-it.yml}.
   */
  private static final int MAX_ATTEMPTS = 4;
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
  @WireMockStub(scripts = {
    "/wiremock/stubs/retry-recovers-first-attempt.json",
    "/wiremock/stubs/retry-recovers-later-attempts.json"})
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void execute_positive_retriesAfterAuthorizationServiceFailure() throws Exception {
    var timerId = createTimer("/test/retry-recovers");

    // AC1 + AC2: the sidecar authorization failure is retried and the second call succeeds
    awaitCallCount("/test/retry-recovers", 2);
    pauseAndAwaitCompletion(timerId);
  }

  @Test
  @WireMockStub("/wiremock/stubs/retry-always-fails.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void execute_negative_stopsAfterConfiguredRetriesAreExhausted() throws Exception {
    var timerId = createTimer("/test/retry-exhausted");

    // AC3: a permanently failing retryable response stops after the configured number of attempts
    awaitStableCallCount("/test/retry-exhausted", MAX_ATTEMPTS, Duration.ofMillis(300));
    pauseAndAwaitCompletion(timerId);
  }

  @Test
  @WireMockStub("/wiremock/stubs/retry-non-retryable.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void execute_negative_doesNotRetryNonRetryableFailure() throws Exception {
    var timerId = createTimer("/test/retry-non-retryable");

    // AC4: generic 503 is not retryable. Stability beyond Retry-After also proves transport retries are disabled.
    awaitStableCallCount("/test/retry-non-retryable", 1, Duration.ofMillis(1200));
    pauseAndAwaitCompletion(timerId);
  }

  private UUID createTimer(String path) throws Exception {
    var timerId = UUID.randomUUID();
    var timerDescriptor = new TimerDescriptor()
      .id(timerId)
      .enabled(true)
      .moduleId(MODULE_ID)
      .routingEntry(new RoutingEntry()
        .methods(List.of("POST"))
        .pathPattern(path)
        .delay("1")
        .unit(MINUTE));

    doPost("/scheduler/timers", timerDescriptor).andExpect(jsonPath("$.enabled", is(true)));
    return timerId;
  }

  private void pauseAndAwaitCompletion(UUID timerId) throws SchedulerException {
    scheduler.pauseTrigger(triggerKey(timerId.toString(), JOB_GROUP));
    var jobKey = jobKey(timerId.toString(), JOB_GROUP);
    await().atMost(TEN_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> assertThat(scheduler.getCurrentlyExecutingJobs())
        .noneMatch(context -> context.getJobDetail().getKey().equals(jobKey)));
  }

  private static void awaitCallCount(String path, int expected) {
    await().atMost(TEN_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> assertThat(timerCallCount(path)).isEqualTo(expected));
  }

  private static void awaitStableCallCount(String path, int expected, Duration stabilityWindow) {
    awaitCallCount(path, expected);
    await().during(stabilityWindow).atMost(TEN_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> assertThat(timerCallCount(path)).isEqualTo(expected));
  }
}
