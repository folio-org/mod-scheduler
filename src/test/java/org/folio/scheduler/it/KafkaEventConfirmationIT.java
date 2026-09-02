package org.folio.scheduler.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.integration.kafka.model.ResourceEventType.DELETE;
import static org.folio.integration.kafka.model.ResourceEventType.UPDATE;
import static org.folio.integration.kafka.model.ResourceResultStatus.FAILURE;
import static org.folio.integration.kafka.model.ResourceResultStatus.SUCCESS;
import static org.folio.scheduler.domain.dto.TimerUnit.SECOND;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.utils.TestUtils.await;
import static org.folio.test.FakeKafkaConsumer.getEvents;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.quartz.impl.matchers.GroupMatcher.anyJobGroup;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;

import java.util.List;
import org.folio.integration.kafka.model.ResourceEvent;
import org.folio.integration.kafka.model.ResourceEventType;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.integration.kafka.model.ResourceResultStatus;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.integration.kafka.model.ScheduledTimers;
import org.folio.scheduler.service.RequestOrigin;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.spring.liquibase.LiquibaseMigrationLockService;
import org.folio.test.FakeKafkaConsumer;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

@EnableKeycloakTlsMode
@IntegrationTest
@TestPropertySource(properties = "application.event-confirmation.enabled=true")
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class KafkaEventConfirmationIT extends BaseIntegrationTest {

  private static final String SCHEDULED_TIMER_TOPIC = "it.test.mgr-tenant-entitlements.scheduled-job";
  private static final String CONFIRMATION_TOPIC = "it.mgr-tenant-entitlements.resource-result";
  private static final String EVENT_ID = "5f26fe20-d7bc-11ef-9cd2-0242ac120002";
  private static final String RESOURCE_NAME = "Scheduled Job";
  private static final String MODULE_ID = "mod-foo-1.0.0";

  @MockitoSpyBean private SchedulerTimerService schedulerTimerService;
  @MockitoBean private LiquibaseMigrationLockService liquibaseMigrationLockService;
  @Autowired private KafkaTemplate<String, ResourceEvent<ScheduledTimers>> kafkaTemplate;
  @Autowired private Scheduler scheduler;

  @BeforeAll
  static void beforeAll(@Autowired KafkaAdmin kafkaAdmin, @Autowired FakeKafkaConsumer fakeKafkaConsumer) {
    createTopic(SCHEDULED_TIMER_TOPIC, kafkaAdmin);
    createTopic(CONFIRMATION_TOPIC, kafkaAdmin);
    fakeKafkaConsumer.registerTopic(CONFIRMATION_TOPIC, ResourceResultEvent.class);
    setUpTenant();
  }

  @AfterAll
  static void afterAll(@Autowired Scheduler quartzScheduler) throws Exception {
    removeTenant();
    deleteAllQuartzJobs(quartzScheduler);
  }

  @BeforeEach
  void beforeEach() {
    FakeKafkaConsumer.removeAllEvents();
  }

  @AfterEach
  void afterEach() throws Exception {
    for (JobKey jobKey : scheduler.getJobKeys(anyJobGroup())) {
      scheduler.deleteJob(jobKey);
    }
  }

  @Test
  @WireMockStub("/wiremock/stubs/timer-endpoint.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void createTimers_positive_publishesSuccessConfirmation() {
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, resourceEvent(CREATE));

    awaitConfirmation(EVENT_ID, TENANT_ID, RESOURCE_NAME, MODULE_ID, SUCCESS, false);
  }

  @Test
  @WireMockStub("/wiremock/stubs/timer-endpoint.json")
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void updateTimers_positive_publishesSuccessConfirmation() {
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, resourceEvent(UPDATE));

    awaitConfirmation(EVENT_ID, TENANT_ID, RESOURCE_NAME, MODULE_ID, SUCCESS, false);
  }

  @Test
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void deleteTimers_positive_publishesSuccessConfirmation() {
    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, resourceEvent(DELETE));

    awaitConfirmation(EVENT_ID, TENANT_ID, RESOURCE_NAME, MODULE_ID, SUCCESS, false);
  }

  @Test
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void createTimers_negative_processingFailure_publishesFailureConfirmation() {
    doThrow(new RuntimeException("timer creation failed"))
      .when(schedulerTimerService).create(any(), eq(RequestOrigin.KAFKA));

    kafkaTemplate.send(SCHEDULED_TIMER_TOPIC, resourceEvent(CREATE));

    awaitConfirmation(EVENT_ID, TENANT_ID, RESOURCE_NAME, MODULE_ID, FAILURE, true);
  }

  private static void awaitConfirmation(String id, String tenant, String resourceName,
    String moduleId, ResourceResultStatus status, boolean withDetails) {
    await().untilAsserted(() -> {
      var events = getEvents(CONFIRMATION_TOPIC, ResourceResultEvent.class);
      assertThat(events).hasSize(1);
      var c = events.getFirst().value();
      assertThat(c.getId()).isEqualTo(id);
      assertThat(c.getTenant()).isEqualTo(tenant);
      assertThat(c.getResourceName()).isEqualTo(resourceName);
      assertThat(c.getModuleId()).isEqualTo(moduleId);
      assertThat(c.getStatus()).isEqualTo(status);
      if (withDetails) {
        assertThat(c.getDetails()).isNotNull();
      }
    });
  }

  private static ResourceEvent<ScheduledTimers> resourceEvent(ResourceEventType type) {
    var scheduledTimers = new ScheduledTimers()
      .moduleId(MODULE_ID)
      .applicationId("app-foo-1.0.0")
      .timers(List.of(routingEntry()));

    return switch (type) {
      case CREATE, UPDATE -> ResourceEvent.<ScheduledTimers>baseBuilder()
        .id(EVENT_ID).type(type).resourceName(RESOURCE_NAME).tenant(TENANT_ID)
        .newValue(scheduledTimers).build();
      case DELETE -> ResourceEvent.<ScheduledTimers>baseBuilder()
        .id(EVENT_ID).type(type).resourceName(RESOURCE_NAME).tenant(TENANT_ID)
        .oldValue(scheduledTimers).build();
      default -> throw new UnsupportedOperationException("Unsupported event type: " + type);
    };
  }

  private static RoutingEntry routingEntry() {
    return new RoutingEntry()
      .methods(List.of("POST"))
      .pathPattern("/test")
      .delay("1")
      .unit(SECOND);
  }
}
