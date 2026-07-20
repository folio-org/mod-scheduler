package org.folio.scheduler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.support.TestConstants.MODULE_NAME;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_UUID;
import static org.folio.scheduler.support.TestConstants.USER_ID_UUID;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.quartz.JobKey.jobKey;

import org.folio.scheduler.domain.dto.TimerType;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.JobDetail;
import org.quartz.impl.JobDetailImpl;

@UnitTest
class ScheduledJobDetailTest {

  private static final String TIMER_TYPE_DATA_FIELD = "timer-type";
  private static final String JOB_GROUP = TENANT_ID + "#" + MODULE_NAME;

  @Test
  void builder_positive_userTimer() {
    var jobDetail = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID)
      .moduleName(MODULE_NAME)
      .timerType(TimerType.USER)
      .userId(USER_ID_UUID)
      .build();

    assertThat(jobDetail.getId()).isEqualTo(TIMER_UUID);
    assertThat(jobDetail.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(jobDetail.getModuleName()).isEqualTo(MODULE_NAME);
    assertThat(jobDetail.getTimerType()).isEqualTo(TimerType.USER);
    assertThat(jobDetail.getUserId()).isEqualTo(USER_ID_UUID);
  }

  @Test
  void builder_positive_systemTimerWithoutUserId() {
    var jobDetail = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID)
      .moduleName(MODULE_NAME)
      .timerType(TimerType.SYSTEM)
      .build();

    assertThat(jobDetail.getTimerType()).isEqualTo(TimerType.SYSTEM);
    assertThat(jobDetail.getUserId()).isNull();
  }

  @Test
  void builder_negative_nullId() {
    var builder = ScheduledJobDetail.builder()
      .tenantId(TENANT_ID)
      .moduleName(MODULE_NAME)
      .timerType(TimerType.SYSTEM);

    assertThatThrownBy(builder::build)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("id must not be null");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void builder_negative_blankTenantId(String tenantId) {
    var builder = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(tenantId)
      .moduleName(MODULE_NAME)
      .timerType(TimerType.SYSTEM);

    assertThatThrownBy(builder::build)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("tenantId must not be blank");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void builder_negative_blankModuleName(String moduleName) {
    var builder = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID)
      .moduleName(moduleName)
      .timerType(TimerType.SYSTEM);

    assertThatThrownBy(builder::build)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("moduleName must not be blank");
  }

  @Test
  void builder_negative_nullTimerType() {
    var builder = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID)
      .moduleName(MODULE_NAME);

    assertThatThrownBy(builder::build)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("timerType must not be null");
  }

  @Test
  void jobGroup_positive_combinesTenantAndModule() {
    assertThat(ScheduledJobDetail.jobGroup(TENANT_ID, MODULE_NAME)).isEqualTo(JOB_GROUP);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void jobGroup_negative_blankTenant(String tenantId) {
    assertThatThrownBy(() -> ScheduledJobDetail.jobGroup(tenantId, MODULE_NAME))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("tenantId must not be blank");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void jobGroup_negative_blankModule(String moduleName) {
    assertThatThrownBy(() -> ScheduledJobDetail.jobGroup(TENANT_ID, moduleName))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("moduleName must not be blank");
  }

  @Test
  void toQuartzJobDetail_positive_userTimer() {
    var jobDetail = userTimer().toQuartzJobDetail();

    assertThat(jobDetail.getKey().getName()).isEqualTo(TIMER_ID);
    assertThat(jobDetail.getKey().getGroup()).isEqualTo(JOB_GROUP);
    var jobDataMap = jobDetail.getJobDataMap();
    assertThat(jobDataMap.getString(TENANT)).isEqualTo(TENANT_ID);
    assertThat(jobDataMap.getString(TIMER_TYPE_DATA_FIELD)).isEqualTo("user");
    assertThat(jobDataMap.getString(USER_ID)).isEqualTo(USER_ID_UUID.toString());
  }

  @Test
  void toQuartzJobDetail_positive_systemTimerOmitsUserId() {
    var jobDetail = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID)
      .moduleName(MODULE_NAME)
      .timerType(TimerType.SYSTEM)
      .build()
      .toQuartzJobDetail();

    assertThat(jobDetail.getKey().getGroup()).isEqualTo(JOB_GROUP);
    var jobDataMap = jobDetail.getJobDataMap();
    assertThat(jobDataMap.getString(TIMER_TYPE_DATA_FIELD)).isEqualTo("system");
    assertThat(jobDataMap.containsKey(USER_ID)).isFalse();
  }

  @Test
  void fromQuartzJobDetail_positive_parsesModuleNameFromGroup() {
    var result = ScheduledJobDetail.fromQuartzJobDetail(userTimer().toQuartzJobDetail());

    assertThat(result.getId()).isEqualTo(TIMER_UUID);
    assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(result.getModuleName()).isEqualTo(MODULE_NAME);
    assertThat(result.getTimerType()).isEqualTo(TimerType.USER);
    assertThat(result.getUserId()).isEqualTo(USER_ID_UUID);
  }

  @Test
  void fromQuartzJobDetail_positive_blankUserIdResolvesToNull() {
    var result = ScheduledJobDetail.fromQuartzJobDetail(rawJobDetail("system", ""));

    assertThat(result.getTimerType()).isEqualTo(TimerType.SYSTEM);
    assertThat(result.getUserId()).isNull();
  }

  @Test
  void fromQuartzJobDetail_negative_unknownTimerType() {
    var quartzJobDetail = rawJobDetail("unknown", null);

    assertThatThrownBy(() -> ScheduledJobDetail.fromQuartzJobDetail(quartzJobDetail))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Unexpected value 'unknown'");
  }

  @Test
  void roundTrip_positive_preservesAllFields() {
    var original = userTimer();

    var result = ScheduledJobDetail.fromQuartzJobDetail(original.toQuartzJobDetail());

    assertEquals(original, result);
  }

  private static ScheduledJobDetail userTimer() {
    return ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID)
      .moduleName(MODULE_NAME)
      .timerType(TimerType.USER)
      .userId(USER_ID_UUID)
      .build();
  }

  private static JobDetail rawJobDetail(String timerType, String userId) {
    var jobDetail = new JobDetailImpl();
    jobDetail.setKey(jobKey(TIMER_ID, JOB_GROUP));
    jobDetail.getJobDataMap().put(TENANT, TENANT_ID);
    jobDetail.getJobDataMap().put(TIMER_TYPE_DATA_FIELD, timerType);
    if (userId != null) {
      jobDetail.getJobDataMap().put(USER_ID, userId);
    }
    return jobDetail;
  }
}
