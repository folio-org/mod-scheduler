package org.folio.scheduler.service;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.quartz.JobBuilder.newJob;

import java.util.UUID;
import lombok.Builder;
import lombok.Value;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.service.jobs.OkapiHttpRequestExecutor;
import org.quartz.JobDetail;

@Value
public class ScheduledJobDetail {

  private static final String TIME_TYPE_DATA_FIELD = "timer-type";

  UUID id;
  String tenantId;
  TimerType timerType;
  UUID userId;

  @Builder
  private ScheduledJobDetail(UUID id, String tenantId, TimerType timerType, UUID userId) {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    if (isBlank(tenantId)) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    if (timerType == null) {
      throw new IllegalArgumentException("timerType must not be null");
    }

    this.id = id;
    this.tenantId = tenantId;
    this.timerType = timerType;
    this.userId = userId;
  }

  public static ScheduledJobDetail fromQuartzJobDetail(JobDetail jobDetail) {
    var jobDataMap = jobDetail.getJobDataMap();

    return ScheduledJobDetail.builder()
      .id(UUID.fromString(jobDetail.getKey().getName()))
      .tenantId(jobDataMap.getString(TENANT))
      .timerType(TimerType.fromValue(jobDataMap.getString(TIME_TYPE_DATA_FIELD)))
      .userId(UUID.fromString(jobDataMap.getString(USER_ID)))
      .build();
  }

  public JobDetail toQuartzJobDetail() {
    return newJob(OkapiHttpRequestExecutor.class)
      .withIdentity(id.toString())
      .usingJobData(TENANT, tenantId)
      .usingJobData(TIME_TYPE_DATA_FIELD, timerType.getValue())
      .usingJobData(USER_ID, userId.toString())
      .build();
  }
}
