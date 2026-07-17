package org.folio.scheduler.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.folio.test.TestUtils.asJsonString;
import static org.quartz.JobKey.jobKey;
import static org.quartz.impl.matchers.GroupMatcher.anyJobGroup;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.RoutingEntrySchedule;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.support.TestConstants;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies that Quartz jobs from different tenants are isolated by the {@code <tenant>#<moduleName>} group in the
 * shared, clustered job store: two tenants scheduling a timer for the same module land in distinct groups.
 */
@EnableKeycloakTlsMode
@IntegrationTest
class SchedulerTimerJobGroupIT extends BaseIntegrationTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String MODULE_NAME = "mod-foo";

  @Autowired private Scheduler scheduler;

  @BeforeAll
  static void beforeAll() {
    enableTenant(TENANT_A);
    enableTenant(TENANT_B);
  }

  @AfterAll
  static void afterAll(@Autowired Scheduler scheduler) throws Exception {
    removeTenant(TENANT_A);
    removeTenant(TENANT_B);
    deleteAllQuartzJobs(scheduler);
    assertThat(scheduler.getJobKeys(anyJobGroup())).isEmpty();
  }

  @Test
  void schedule_positive_jobsAreIsolatedByTenantAndModuleGroup() throws Exception {
    var timerA = UUID.randomUUID();
    var timerB = UUID.randomUUID();
    createTimer(TENANT_A, timerA);
    createTimer(TENANT_B, timerB);

    // each tenant's job lives in its own <tenant>#<moduleName> group
    assertThat(scheduler.checkExists(jobKey(timerA.toString(), TENANT_A + "#" + MODULE_NAME))).isTrue();
    assertThat(scheduler.checkExists(jobKey(timerB.toString(), TENANT_B + "#" + MODULE_NAME))).isTrue();

    // and is not reachable under the other tenant's group
    assertThat(scheduler.checkExists(jobKey(timerA.toString(), TENANT_B + "#" + MODULE_NAME))).isFalse();
    assertThat(scheduler.checkExists(jobKey(timerB.toString(), TENANT_A + "#" + MODULE_NAME))).isFalse();
  }

  private static void createTimer(String tenant, UUID timerId) throws Exception {
    // a far-future cron so the job is scheduled but does not fire (no impersonation) during the test
    var body = new TimerDescriptor()
      .id(timerId)
      .enabled(true)
      .moduleId(MODULE_ID)
      .routingEntry(new RoutingEntry()
        .methods(List.of("POST"))
        .pathPattern("/test")
        .schedule(new RoutingEntrySchedule().cron("0 0 1 1 *")));

    mockMvc.perform(post("/scheduler/timers")
        .header(TENANT, tenant)
        .header(USER_ID, TestConstants.USER_ID)
        .content(asJsonString(body))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isCreated());
  }
}
