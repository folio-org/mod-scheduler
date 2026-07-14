package org.folio.scheduler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_UUID;
import static org.folio.scheduler.support.TestConstants.USER_ID_UUID;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

  @Test
  void builder_positive_userTimer() {
    var jobDetail = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID)
      .timerType(TimerType.USER)
      .userId(USER_ID_UUID)
      .build();

    assertThat(jobDetail.getId()).isEqualTo(TIMER_UUID);
    assertThat(jobDetail.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(jobDetail.getTimerType()).isEqualTo(TimerType.USER);
    assertThat(jobDetail.getUserId()).isEqualTo(USER_ID_UUID);
  }

  @Test
  void builder_positive_systemTimerWithoutUserId() {
    var jobDetail = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID)
      .timerType(TimerType.SYSTEM)
      .build();

    assertThat(jobDetail.getTimerType()).isEqualTo(TimerType.SYSTEM);
    assertThat(jobDetail.getUserId()).isNull();
  }

  @Test
  void builder_negative_nullId() {
    var builder = ScheduledJobDetail.builder()
      .tenantId(TENANT_ID)
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
      .timerType(TimerType.SYSTEM);

    assertThatThrownBy(builder::build)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("tenantId must not be blank");
  }

  @Test
  void builder_negative_nullTimerType() {
    var builder = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID);

    assertThatThrownBy(builder::build)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("timerType must not be null");
  }

  @Test
  void toQuartzJobDetail_positive_userTimer() {
    var jobDetail = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID)
      .timerType(TimerType.USER)
      .userId(USER_ID_UUID)
      .build()
      .toQuartzJobDetail();

    assertThat(jobDetail.getKey().getName()).isEqualTo(TIMER_ID);
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
      .timerType(TimerType.SYSTEM)
      .build()
      .toQuartzJobDetail();

    var jobDataMap = jobDetail.getJobDataMap();
    assertThat(jobDataMap.getString(TENANT)).isEqualTo(TENANT_ID);
    assertThat(jobDataMap.getString(TIMER_TYPE_DATA_FIELD)).isEqualTo("system");
    assertThat(jobDataMap.containsKey(USER_ID)).isFalse();
  }

  @Test
  void fromQuartzJobDetail_positive_userTimer() {
    var quartzJobDetail = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID)
      .timerType(TimerType.USER)
      .userId(USER_ID_UUID)
      .build()
      .toQuartzJobDetail();

    var result = ScheduledJobDetail.fromQuartzJobDetail(quartzJobDetail);

    assertThat(result.getId()).isEqualTo(TIMER_UUID);
    assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(result.getTimerType()).isEqualTo(TimerType.USER);
    assertThat(result.getUserId()).isEqualTo(USER_ID_UUID);
  }

  @Test
  void fromQuartzJobDetail_positive_systemTimerWithoutUserId() {
    var quartzJobDetail = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID)
      .timerType(TimerType.SYSTEM)
      .build()
      .toQuartzJobDetail();

    var result = ScheduledJobDetail.fromQuartzJobDetail(quartzJobDetail);

    assertThat(result.getTimerType()).isEqualTo(TimerType.SYSTEM);
    assertThat(result.getUserId()).isNull();
  }

  @Test
  void fromQuartzJobDetail_positive_blankUserIdResolvesToNull() {
    var quartzJobDetail = rawJobDetail("system", "");

    var result = ScheduledJobDetail.fromQuartzJobDetail(quartzJobDetail);

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
    var original = ScheduledJobDetail.builder()
      .id(TIMER_UUID)
      .tenantId(TENANT_ID)
      .timerType(TimerType.USER)
      .userId(USER_ID_UUID)
      .build();

    var result = ScheduledJobDetail.fromQuartzJobDetail(original.toQuartzJobDetail());

    assertEquals(original, result);
  }

  private static JobDetail rawJobDetail(String timerType, String userId) {
    var jobDetail = new JobDetailImpl();
    jobDetail.setName(TIMER_ID);
    jobDetail.getJobDataMap().put(TENANT, TENANT_ID);
    jobDetail.getJobDataMap().put(TIMER_TYPE_DATA_FIELD, timerType);
    if (userId != null) {
      jobDetail.getJobDataMap().put(USER_ID, userId);
    }
    return jobDetail;
  }
}
