package org.folio.scheduler.it;

import static org.folio.scheduler.support.TestConstants.MODULE_ID;
import static org.folio.scheduler.support.TestConstants.USER_ID;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerUnit;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

/**
 * Verifies the {@code application.timer.api.allow-user-id-update} behaviour, which cannot be exercised from
 * {@link SchedulerTimerIT} because it is a static configuration flag; it needs its own application context.
 */
@EnableKeycloakTlsMode
@IntegrationTest
@TestPropertySource(properties = "application.timer.api.allow-user-id-update=true")
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class SchedulerTimerUserIdUpdateIT extends BaseIntegrationTest {

  private static final String USER_B_ID = "5d07750b-22ce-4f42-864a-3e476e6992e8";

  @BeforeAll
  static void beforeAll() {
    setUpTenant();
  }

  @AfterAll
  static void afterAll() {
    removeTenant();
  }

  @Test
  void update_positive_refreshesUserIdToUpdatingUser() throws Exception {
    var timerId = UUID.randomUUID();
    doPost("/scheduler/timers", timerDescriptor(timerId))
      .andExpect(jsonPath("$.userId", is(USER_ID)));

    attemptPutAsUser("/scheduler/timers/{id}", USER_B_ID, timerDescriptor(timerId), timerId.toString())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.userId", is(USER_B_ID)));
  }

  private static TimerDescriptor timerDescriptor(UUID timerId) {
    return new TimerDescriptor()
      .id(timerId)
      .enabled(false)
      .moduleId(MODULE_ID)
      .routingEntry(new RoutingEntry()
        .methods(List.of("POST"))
        .pathPattern("/test")
        .delay("1")
        .unit(TimerUnit.SECOND));
  }
}
