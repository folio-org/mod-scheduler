package org.folio.scheduler.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.awaitility.Durations.TWO_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TWO_SECONDS;
import static org.folio.scheduler.domain.dto.TimerUnit.SECOND;
import static org.folio.scheduler.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.utils.TestUtils.asJsonString;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.quartz.impl.matchers.GroupMatcher.anyJobGroup;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.testcontainers.shaded.org.awaitility.Durations.FIVE_HUNDRED_MILLISECONDS;

import java.sql.SQLDataException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerDescriptorList;
import org.folio.scheduler.integration.kafka.model.ResourceEvent;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableKeycloak;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.hibernate.exception.SQLGrammarException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.shaded.org.awaitility.core.ConditionFactory;

@Log4j2
@EnableKeycloak(tlsEnabled = true)
@IntegrationTest
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class KafkaMessageListenerIT extends BaseIntegrationTest {

  private static final String SCHEDULED_TIMER_TOPIC = "it.test.mgr-tenant-entitlements.scheduled-job";

  @SpyBean private SchedulerTimerService schedulerTimerService;
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
  void handleScheduledJobEvent_positive() {
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, asJsonString(resourceEvent()));
    await().untilAsserted(() -> getScheduledTimers(timerDescriptorList(timerDescriptor()))
      .andExpect(jsonPath("$.timerDescriptors[0].id").hasJsonPath()));

    await().atMost(TWO_SECONDS).pollDelay(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(BaseIntegrationTest::verifyTimerRequestCallsCount);
  }

  @Test
  @WireMockStub("/wiremock/stubs/timer-endpoint.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void handleScheduledJobEvent_positive_eventIsSentWhenTenantIsDisabled() {
    var resourceEventJson = asJsonString(resourceEvent());
    var expectedTimerDescriptors = timerDescriptorList(timerDescriptor());
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, resourceEventJson);
    await().untilAsserted(() -> getScheduledTimers(expectedTimerDescriptors));

    removeTenant();
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, resourceEventJson);
    awaitFor(FIVE_HUNDRED_MILLISECONDS);

    setUpTenant();
    await().untilAsserted(() -> getScheduledTimers(expectedTimerDescriptors));
  }

  @MethodSource("exceptionDataProvider")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  @ParameterizedTest(name = "[{index}] name={0}")
  @DisplayName("handleScheduledJobEvent_negative_parameterizedForNonRetryableExceptions")
  void handleScheduledJobEvent_negative_parameterized(@SuppressWarnings("unused") String name, Throwable throwable)
    throws Exception {
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, asJsonString(resourceEvent()));
    doThrow(throwable).when(schedulerTimerService).create(any());

    awaitFor(ONE_SECOND);

    getScheduledTimers(timerDescriptorList());
  }

  private static Stream<Arguments> exceptionDataProvider() {
    return Stream.of(
      arguments("RuntimeException", new RuntimeException("error")),

      arguments("InvalidDataAccessResourceUsageException without cause",
        new InvalidDataAccessResourceUsageException("invalid data access error")),

      arguments("InvalidDataAccessResourceUsageException with RuntimeException cause",
        new InvalidDataAccessResourceUsageException("invalid data access error", new RuntimeException("error"))),

      arguments("InvalidDataAccessResourceUsageException with SQLGrammarExceptionCause",
        new InvalidDataAccessResourceUsageException("invalid data access error",
          new SQLGrammarException("sql grammar error", null))),

      arguments("InvalidDataAccessResourceUsageException with SQLGrammarExceptionCause, but cause is not PSQLException",
        new InvalidDataAccessResourceUsageException("invalid data access error",
          new SQLGrammarException("sql grammar error", new SQLDataException()))),

      arguments("InvalidDataAccessResourceUsageException with PSQLException cause",
        new InvalidDataAccessResourceUsageException("invalid data access error",
          new SQLGrammarException("sql grammar error", new PSQLException("unknown", null)))),

      arguments("InvalidDataAccessResourceUsageException with PSQLException cause, but non-retryable message 1",
        new InvalidDataAccessResourceUsageException("invalid data access error",
          new SQLGrammarException("sql grammar error", new PSQLException("ERROR: relation is invalid", null)))),

      arguments("InvalidDataAccessResourceUsageException with PSQLException cause, but non-retryable message 1",
        new InvalidDataAccessResourceUsageException("invalid data access error",
          new SQLGrammarException("sql grammar error", new PSQLException("'capability' table does not exist", null))))
    );
  }

  private static ResultActions getScheduledTimers(TimerDescriptorList timerDescriptorList) throws Exception {
    return doGet("/scheduler/timers")
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(content().json(asJsonString(timerDescriptorList)));
  }

  private static ConditionFactory await() {
    return Awaitility.await().atMost(ONE_MINUTE).pollInterval(TWO_HUNDRED_MILLISECONDS);
  }

  /**
   * Sonar friendly Thread.sleep(millis) implementation
   *
   * @param duration - duration to await.
   */
  @SuppressWarnings({"SameParameterValue"})
  private static void awaitFor(Duration duration) {
    var sampleResult = Optional.of(1);
    Awaitility.await()
      .pollInSameThread()
      .atMost(duration.plus(Duration.ofMillis(250)))
      .pollDelay(duration)
      .untilAsserted(() -> assertThat(sampleResult).isPresent());
  }

  private static TimerDescriptor timerDescriptor() {
    return new TimerDescriptor().enabled(true).routingEntry(routingEntry());
  }

  private static TimerDescriptorList timerDescriptorList(TimerDescriptor... timerDescriptors) {
    return new TimerDescriptorList()
      .totalRecords(timerDescriptors.length)
      .timerDescriptors(List.of(timerDescriptors));
  }

  private static ResourceEvent resourceEvent() {
    return new ResourceEvent()
      .resourceName("Scheduled Job")
      .tenant(TENANT_ID)
      .type(CREATE)
      .newValue(routingEntry());
  }

  private static RoutingEntry routingEntry() {
    return new RoutingEntry()
      .methods(List.of("POST"))
      .pathPattern("/test")
      .delay("1")
      .unit(SECOND);
  }
}
