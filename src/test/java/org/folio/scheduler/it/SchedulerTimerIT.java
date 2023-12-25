package org.folio.scheduler.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TWO_SECONDS;
import static org.folio.scheduler.domain.dto.TimerUnit.SECOND;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.quartz.TriggerKey.triggerKey;
import static org.quartz.impl.matchers.GroupMatcher.anyJobGroup;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.RoutingEntrySchedule;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.support.TestValues;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.scheduler.support.extension.EnableKeycloak;
import org.folio.scheduler.support.extension.KeycloakRealms;
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

@EnableKeycloak
@IntegrationTest
@Sql(scripts = "classpath:/sql/timer-descriptor-it.sql", executionPhase = BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class SchedulerTimerIT extends BaseIntegrationTest {

  private static final String TIMER_ID = "123e4567-e89b-12d3-a456-426614174000";
  private static final String UNKNOWN_ID = "51fd5dff-5d51-4169-a296-d441e1d234c9";
  private static final UUID TIMER_ID_TO_UPDATE = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
  private static final String TIMER_ID_TO_DELETE = "123e4567-e89b-12d3-a456-426614174002";

  @Autowired private Scheduler scheduler;

  @BeforeAll
  static void beforeAll() {
    setUpTenant();
  }

  @AfterAll
  static void afterAll(@Autowired Scheduler scheduler) throws Exception {
    removeTenant();
    assertThat(scheduler.getJobKeys(anyJobGroup())).isEmpty();
  }

  @AfterEach
  void tearDown() throws SchedulerException {
    scheduler.clear();
  }

  @Test
  void getById_positive() throws Exception {
    doGet("/scheduler/timers/{id}", TIMER_ID)
      .andExpect(jsonPath("$.id", is(TIMER_ID)))
      .andExpect(jsonPath("$.enabled", is(true)));
  }

  @Test
  void getAll_positive() throws Exception {
    doGet("/scheduler/timers")
      .andExpect(jsonPath("$.totalRecords", is(3)));
  }

  @Test
  @WireMockStub("/wiremock/stubs/timer-endpoint.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void create_positive_simpleTrigger() throws Exception {
    var timerId = UUID.randomUUID().toString();
    var timerDescriptor = new TimerDescriptor()
      .id(UUID.fromString(timerId))
      .enabled(true)
      .routingEntry(new RoutingEntry()
        .methods(List.of("POST"))
        .pathPattern("/test")
        .delay("1")
        .unit(SECOND));

    var timestampBeforeSavingDesc = Instant.now();
    doPost("/scheduler/timers", timerDescriptor)
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.enabled", is(true)));

    var nextFireTime = scheduler.getTrigger(triggerKey(timerId)).getNextFireTime().toInstant();
    assertThat(nextFireTime).isAfter(timestampBeforeSavingDesc).isBefore(timestampBeforeSavingDesc.plusSeconds(1));

    await().atMost(TWO_SECONDS).pollDelay(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(BaseIntegrationTest::verifyTimerRequestCallsCount);
  }

  @Test
  @WireMockStub("/wiremock/stubs/timer-endpoint.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void create_positive_cronTrigger() throws Exception {
    var timerId = UUID.randomUUID().toString();
    var timerDescriptor = new TimerDescriptor()
      .id(UUID.fromString(timerId))
      .enabled(true)
      .routingEntry(new RoutingEntry()
        .methods(List.of("POST"))
        .pathPattern("/test")
        .schedule(new RoutingEntrySchedule().cron("*/1 * * * *")));

    var timestampBeforeSavingDesc = Instant.now();
    doPost("/scheduler/timers", timerDescriptor)
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.enabled", is(true)));

    var nextFireTime = scheduler.getTrigger(triggerKey(timerId)).getNextFireTime().toInstant();
    assertThat(nextFireTime).isBefore(timestampBeforeSavingDesc.plusSeconds(1));

    await().atMost(TWO_SECONDS).pollDelay(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(BaseIntegrationTest::verifyTimerRequestCallsCount);
  }

  @Test
  void update_positive() throws Exception {
    var desc = TestValues.timerDescriptor(TIMER_ID_TO_UPDATE);
    doPut("/scheduler/timers/{id}", desc, TIMER_ID_TO_UPDATE)
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.enabled", is(true)));
  }

  @Test
  void delete_positive() throws Exception {
    doDelete("/scheduler/timers/{id}", TIMER_ID_TO_DELETE);
    doGet("/scheduler/timers")
      .andExpect(jsonPath("$.totalRecords", is(2)));
  }

  @Test
  void delete_negative_notFound() throws Exception {
    attemptDelete("/scheduler/timers/{id}", UNKNOWN_ID)
      .andExpect(status().isNoContent());

    doGet("/scheduler/timers")
      .andExpect(jsonPath("$.totalRecords", is(3)));
  }
}
