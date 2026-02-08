package org.folio.scheduler.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_SECOND;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.folio.scheduler.domain.dto.TimerUnit.SECOND;
import static org.folio.scheduler.support.TestConstants.MODULE_ID;
import static org.folio.scheduler.support.TestConstants.USER_ID;
import static org.folio.scheduler.support.TestConstants.USER_ID_UUID;
import static org.folio.test.TestUtils.parseResponse;
import static org.hamcrest.Matchers.hasSize;
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
import java.util.concurrent.TimeUnit;
import org.folio.scheduler.domain.dto.Metadata;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.RoutingEntrySchedule;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerDescriptorList;
import org.folio.scheduler.support.TestValues;
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

@EnableKeycloakTlsMode
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
      .andExpect(jsonPath("$.enabled", is(true)))
      .andExpect(jsonPath("$.metadata").exists())
      .andExpect(jsonPath("$.metadata.createdDate").exists())
      .andExpect(jsonPath("$.metadata.updatedDate").exists());
  }

  @Test
  void getAll_positive() throws Exception {
    doGet("/scheduler/timers")
      .andExpect(jsonPath("$.totalRecords", is(3)));
  }

  @Test
  @WireMockStub("/wiremock/stubs/user-timer-endpoint.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void create_positive_simpleTrigger() throws Exception {
    var timerId = UUID.randomUUID();
    var timerDescriptor = timerDescriptor(timerId);

    var timestampBeforeSavingDesc = Instant.now();
    doPost("/scheduler/timers", timerDescriptor)
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.enabled", is(true)));

    var nextFireTime = scheduler.getTrigger(triggerKey(timerId.toString())).getNextFireTime().toInstant();
    assertThat(nextFireTime).isAfter(timestampBeforeSavingDesc).isBefore(timestampBeforeSavingDesc.plusSeconds(1));

    await().atMost(TEN_SECONDS).pollDelay(ONE_SECOND)
      .untilAsserted(BaseIntegrationTest::verifyTimerRequestCallsCount);
  }

  @Test
  @WireMockStub("/wiremock/stubs/user-timer-endpoint.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void create_positive_cronTriggerEverySecond() throws Exception {
    var timerId = UUID.randomUUID().toString();
    var timerDescriptor = new TimerDescriptor()
      .id(UUID.fromString(timerId))
      .enabled(true)
      .moduleId(MODULE_ID)
      .routingEntry(new RoutingEntry()
        .methods(List.of("POST"))
        .pathPattern("/test")
        .schedule(new RoutingEntrySchedule().cron("* * * * * ?")));

    var timestampBeforeSavingDesc = Instant.now();
    doPost("/scheduler/timers", timerDescriptor)
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.enabled", is(true)));

    var nextFireTime = scheduler.getTrigger(triggerKey(timerId)).getNextFireTime().toInstant();
    assertThat(nextFireTime).isBefore(timestampBeforeSavingDesc.plusSeconds(1));

    await().atMost(TEN_SECONDS).pollDelay(ONE_SECOND)
      .untilAsserted(BaseIntegrationTest::verifyTimerRequestCallsCount);
  }

  @Test
  void update_positive() throws Exception {
    var desc = TestValues.timerDescriptor(TIMER_ID_TO_UPDATE).moduleId(MODULE_ID);
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

  @Test
  @WireMockStub("/wiremock/stubs/test-sometimer-endpoint.json")
  void create_duplicate() throws Exception {
    var timerDescriptor = new TimerDescriptor()
      .enabled(true)
      .moduleId(MODULE_ID)
      .routingEntry(new RoutingEntry()
        .methods(List.of("POST"))
        .pathPattern("/test/sometimer")
        .delay("1")
        .unit(SECOND));

    var initialTimersCount = parseResponse(doGet("/scheduler/timers").andReturn(), TimerDescriptorList.class)
      .getTotalRecords();

    doPost("/scheduler/timers", timerDescriptor)
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.enabled", is(true)));

    doGet("/scheduler/timers").andExpect(jsonPath("$.timerDescriptors", hasSize(initialTimersCount + 1)));

    doPost("/scheduler/timers", timerDescriptor)
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.enabled", is(true)));

    doGet("/scheduler/timers").andExpect(jsonPath("$.timerDescriptors", hasSize(initialTimersCount + 1)));
  }

  @Test
  @WireMockStub("/wiremock/stubs/user-timer-endpoint.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void create_positive_populatesMetadataInResponse() throws Exception {
    var timerId = UUID.randomUUID();
    var timerDescriptor = timerDescriptor(timerId);

    var result = doPost("/scheduler/timers", timerDescriptor).andReturn();
    var responseBody = parseResponse(result, TimerDescriptor.class);

    var now = Instant.now();
    assertMetadata(responseBody.getMetadata(), USER_ID, now, now);
  }

  @Test
  @WireMockStub("/wiremock/stubs/timer-call-targets.json")
  @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
  void update_positive_refreshesOnlyUpdateFieldsInMetadata() throws Exception {
    var getResult = doGet("/scheduler/timers/{id}", TIMER_ID).andReturn();
    var existingTimer = parseResponse(getResult, TimerDescriptor.class);

    assertThat(existingTimer.getMetadata()).isNotNull();
    var originalCreatedByUserId = existingTimer.getMetadata().getCreatedByUserId();
    var originalCreatedDate = existingTimer.getMetadata().getCreatedDate();

    existingTimer.setEnabled(false);

    var updateResult = doPut("/scheduler/timers/{id}", existingTimer, TIMER_ID).andReturn();
    var updatedTimer = parseResponse(updateResult, TimerDescriptor.class);

    Metadata updatedMetadata = updatedTimer.getMetadata();
    assertThat(updatedMetadata).isNotNull();
    assertThat(updatedMetadata.getCreatedByUserId()).isEqualTo(originalCreatedByUserId);
    assertThat(updatedMetadata.getCreatedDate()).isEqualTo(originalCreatedDate);
    assertThat(updatedMetadata.getUpdatedDate()).isAfter(originalCreatedDate);
    assertThat(updatedMetadata.getUpdatedByUserId()).isEqualTo(USER_ID_UUID);
  }

  private static TimerDescriptor timerDescriptor(UUID timerId) {
    return new TimerDescriptor()
      .id(timerId)
      .enabled(true)
      .moduleId(MODULE_ID)
      .routingEntry(new RoutingEntry()
        .methods(List.of("POST"))
        .pathPattern("/test")
        .delay("1")
        .unit(SECOND));
  }

  private static void assertMetadata(Metadata metadata, String userId, Instant expectedCreatedDate,
    Instant expectedUpdatedDate) {
    assertThat(metadata).isNotNull();

    assertThat(metadata.getCreatedDate()).isNotNull();
    assertThat(metadata.getCreatedDate()).isCloseTo(expectedCreatedDate, TimeUnit.MINUTES.toMillis(1));
    assertThat(metadata.getCreatedByUserId()).isEqualTo(UUID.fromString(userId));

    assertThat(metadata.getUpdatedDate()).isNotNull();
    assertThat(metadata.getUpdatedDate()).isCloseTo(expectedUpdatedDate, TimeUnit.MINUTES.toMillis(1));
    assertThat(metadata.getUpdatedByUserId()).isEqualTo(UUID.fromString(userId));
  }
}
