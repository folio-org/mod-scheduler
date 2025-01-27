package org.folio.scheduler.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.folio.scheduler.integration.kafka.model.EntitlementEventType.ENTITLE;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.utils.TestUtils.asJsonString;
import static org.folio.scheduler.utils.TestUtils.await;
import static org.hamcrest.Matchers.is;
import static org.quartz.impl.matchers.GroupMatcher.anyJobGroup;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.integration.kafka.model.EntitlementEvent;
import org.folio.scheduler.repository.SchedulerTimerRepository;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.spring.FolioModuleMetadata;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.jdbc.Sql;

@Log4j2
@EnableKeycloakTlsMode
@IntegrationTest
@Sql(scripts = "classpath:/sql/timer-repo-it.sql", executionPhase = BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class KafkaMessageListenerEntitlementEventsIT extends BaseIntegrationTest {

  private static final String ENTITLEMENT_EVENTS_TOPIC = "it.test.entitlement";

  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired
  private SchedulerTimerRepository repository;
  @Autowired
  private FolioModuleMetadata folioModuleMetadata;

  @BeforeAll
  static void beforeAll(@Autowired KafkaAdmin kafkaAdmin) {
    createTopic(ENTITLEMENT_EVENTS_TOPIC, kafkaAdmin);
    setUpTenant();
  }

  @AfterAll
  static void afterAll(@Autowired Scheduler scheduler) throws Exception {
    removeTenant();
    assertThat(scheduler.getJobKeys(anyJobGroup())).isEmpty();
  }

  @Test
  @KeycloakRealms("/json/keycloak/test-realm.json")
  void handleEntitlementEvent_positive() {
    kafkaTemplate.send(ENTITLEMENT_EVENTS_TOPIC, asJsonString(entitlementEvent()));

    await().atMost(TEN_SECONDS).pollDelay(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> {
        checkTimerEnabled("123e4567-e89b-12d3-a456-426614174000", true);
        checkTimerEnabled("123e4567-e89b-12d3-a456-426614174001", true);
        checkTimerEnabled("123e4567-e89b-12d3-a456-426614174002", true);
      });
  }

  private static void checkTimerEnabled(String id, boolean enabled) throws Exception {
    doGet("/scheduler/timers/{id}", id)
      .andExpect(jsonPath("$.id", is(id)))
      .andExpect(jsonPath("$.enabled", is(enabled)));
  }

  private static EntitlementEvent entitlementEvent() {
    return new EntitlementEvent()
      .setModuleId("mod-foo-1.0.0")
      .setType(ENTITLE)
      .setTenantName(TENANT_ID);
  }
}
