package org.folio.scheduler.it;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_SECOND;
import static org.awaitility.Durations.TWO_SECONDS;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.scheduler.domain.dto.TimerUnit.SECOND;
import static org.folio.scheduler.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.utils.TestUtils.asJsonString;
import static org.folio.scheduler.utils.TestUtils.await;
import static org.folio.scheduler.utils.TestUtils.awaitFor;
import static org.folio.scheduler.utils.TestUtils.convertValue;
import static org.folio.scheduler.utils.TestUtils.parse;
import static org.folio.scheduler.utils.TestUtils.readString;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.quartz.impl.matchers.GroupMatcher.anyJobGroup;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.shaded.org.awaitility.Durations.FIVE_HUNDRED_MILLISECONDS;

import java.sql.SQLDataException;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.common.utils.SemverUtils;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerDescriptorList;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.integration.kafka.model.ResourceEvent;
import org.folio.scheduler.integration.kafka.model.ScheduledTimers;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableKeycloakTlsMode;
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

@Log4j2
@EnableKeycloakTlsMode
@IntegrationTest
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class KafkaMessageListenerScheduledJobIT extends BaseIntegrationTest {

  private static final String SCHEDULED_TIMER_TOPIC = "it.test.mgr-tenant-entitlements.scheduled-job";
  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String MODULE_NAME = "mod-foo";

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

  @Test
  @WireMockStub("/wiremock/stubs/event-timer-endpoint.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void handleScheduledJobEvent_positive_upgradeEvent() {
    var newTimerEvent = readString("json/events/folio-app1/mod-foo/create-timer-event.json");
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, newTimerEvent);
    var scheduledTimers1 = parse(newTimerEvent, ResourceEvent.class);
    var routingEntries1 = convertValue(scheduledTimers1.getNewValue(), ScheduledTimers.class);
    await().untilAsserted(() -> getScheduledTimers(timerDescriptorList(routingEntries1)));

    var upgradeEvent = readString("json/events/folio-app1/mod-foo/upgrade-timer-event.json");
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, upgradeEvent);
    var scheduledTimers2 = parse(upgradeEvent, ResourceEvent.class);
    var routingEntries2 = convertValue(scheduledTimers2.getNewValue(), ScheduledTimers.class);
    await().untilAsserted(() -> getScheduledTimers(timerDescriptorList(routingEntries2)));
  }

  @Test
  @SneakyThrows
  @WireMockStub("/wiremock/stubs/event-timer-endpoint.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void handleScheduledJobEvent_positive_userTimerExistsAfterDeleteEvent() {
    var userTimerDescriptorRequest =
      parse(readString("json/user/timer/user-timer-request.json"), TimerDescriptor.class);
    doPost("/scheduler/timers", userTimerDescriptorRequest)
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.id").hasJsonPath());

    var createTimerEvent = readString("json/events/folio-app1/mod-foo/create-timer-event.json");
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, createTimerEvent);
    var scheduledTimers1 = parse(createTimerEvent, ResourceEvent.class);
    var routingEntries1 = convertValue(scheduledTimers1.getNewValue(), ScheduledTimers.class);
    var timerDescList1 =
      timerDescriptorList(routingEntries1).addTimerDescriptorsItem(userTimerDescriptorRequest).totalRecords(2);
    await().untilAsserted(() -> getScheduledTimers(timerDescList1));

    var deleteTimerEvent = readString("json/events/folio-app1/mod-foo/delete-timer-event.json");
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, deleteTimerEvent);
    var userTimer = timerDescriptorList(userTimerDescriptorRequest);
    await().untilAsserted(() -> getScheduledTimers(userTimer));
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
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(timerDescriptorList)));
  }

  private static TimerDescriptor timerDescriptor() {
    return new TimerDescriptor().type(TimerType.SYSTEM).enabled(true)
      .moduleId(MODULE_ID).moduleName(MODULE_NAME).routingEntry(routingEntry());
  }

  private static TimerDescriptor timerDescriptor(RoutingEntry routingEntry) {
    return new TimerDescriptor().type(TimerType.SYSTEM).enabled(true)
      .moduleId(MODULE_ID).moduleName(MODULE_NAME).routingEntry(routingEntry);
  }

  private static TimerDescriptorList timerDescriptorList(TimerDescriptor... timerDescriptors) {
    return new TimerDescriptorList()
      .totalRecords(timerDescriptors.length)
      .timerDescriptors(List.of(timerDescriptors));
  }

  private static TimerDescriptorList timerDescriptorList(ScheduledTimers scheduledTimers) {
    var timerDescriptors = mapToTimerDescriptors(scheduledTimers);
    return new TimerDescriptorList().timerDescriptors(timerDescriptors);
  }

  private static List<TimerDescriptor> mapToTimerDescriptors(ScheduledTimers scheduledTimers) {
    return mapItems(scheduledTimers.getTimers(), routingEntry -> {
      var moduleId = scheduledTimers.getModuleId();
      return timerDescriptor(routingEntry).moduleId(moduleId).moduleName(SemverUtils.getName(moduleId));
    });
  }

  private static ResourceEvent resourceEvent() {
    return new ResourceEvent()
      .resourceName("Scheduled Job")
      .tenant(TENANT_ID)
      .type(CREATE)
      .newValue(scheduledTimers(routingEntry()));
  }

  private static ScheduledTimers scheduledTimers(RoutingEntry... routingEntries) {
    return new ScheduledTimers()
      .moduleId(MODULE_ID)
      .applicationId("app-foo-1.0.0")
      .timers(asList(routingEntries));
  }

  private static RoutingEntry routingEntry() {
    return new RoutingEntry()
      .methods(List.of("POST"))
      .pathPattern("/test")
      .delay("1")
      .unit(SECOND);
  }
}
