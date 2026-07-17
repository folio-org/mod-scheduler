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

/**
 * Immutable, strongly typed representation of the data required to schedule and execute a timer job.
 *
 * <p>
 * It acts as an adapter between the application domain and the Quartz {@link JobDetail}: it can be converted to a
 * {@link JobDetail} for scheduling via {@link #toQuartzJobDetail()} and reconstructed from one at execution time via
 * {@link #fromQuartzJobDetail(JobDetail)}.
 * </p>
 */
@Value
public final class ScheduledJobDetail {

  private static final String TIME_TYPE_DATA_FIELD = "timer-type";
  private static final String GROUP_SEPARATOR = "#";

  /**
   * Unique identifier of the timer; used as the Quartz job key name. Never {@code null}.
   */
  UUID id;

  /**
   * Identifier of the tenant that owns the timer. Never blank.
   */
  String tenantId;

  /**
   * Name of the module the timer belongs to. Never blank. Together with {@link #tenantId} it forms the Quartz job and
   * trigger group (see {@link #jobGroup(String, String)}).
   */
  String moduleName;

  /**
   * Type of the timer, determining how the request is impersonated at execution time. Never {@code null}.
   */
  TimerType timerType;

  /**
   * Identifier of the user on whose behalf the request is executed. Populated for {@link TimerType#USER} timers and
   * left {@code null} for {@link TimerType#SYSTEM} timers, whose user is resolved from the tenant's system user at
   * execution time.
   */
  UUID userId;

  /**
   * Creates a new {@link ScheduledJobDetail}, validating the required fields.
   *
   * @param id - unique timer identifier, must not be {@code null}
   * @param tenantId - owning tenant identifier, must not be blank
   * @param moduleName - owning module name, must not be blank
   * @param timerType - timer type, must not be {@code null}
   * @param userId - user identifier, may be {@code null} for system timers
   * @throws IllegalArgumentException if {@code id} or {@code timerType} is {@code null}, or {@code tenantId} or
   *     {@code moduleName} is blank
   */
  @Builder
  private ScheduledJobDetail(UUID id, String tenantId, String moduleName, TimerType timerType, UUID userId) {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    if (isBlank(tenantId)) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    if (isBlank(moduleName)) {
      throw new IllegalArgumentException("moduleName must not be blank");
    }
    if (timerType == null) {
      throw new IllegalArgumentException("timerType must not be null");
    }

    this.id = id;
    this.tenantId = tenantId;
    this.moduleName = moduleName;
    this.timerType = timerType;
    this.userId = userId;
  }

  /**
   * Builds the Quartz job and trigger group for a timer as {@code <tenant>#<moduleName>}.
   *
   * @param tenantId - owning tenant identifier, must not be blank
   * @param moduleName - owning module name, must not be blank
   * @return the group value
   * @throws IllegalArgumentException if {@code tenantId} or {@code moduleName} is blank
   */
  public static String jobGroup(String tenantId, String moduleName) {
    if (isBlank(tenantId)) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    if (isBlank(moduleName)) {
      throw new IllegalArgumentException("moduleName must not be blank");
    }
    return tenantId + GROUP_SEPARATOR + moduleName;
  }

  /**
   * Reconstructs a {@link ScheduledJobDetail} from a Quartz {@link JobDetail}.
   *
   * <p>
   * The timer id is read from the job key name; the tenant, timer type and user id are read from the job data map; and
   * the module name is parsed from the job key group ({@code <tenant>#<moduleName>}).
   * </p>
   *
   * @param jobDetail - Quartz job detail to convert
   * @return the reconstructed {@link ScheduledJobDetail}
   */
  public static ScheduledJobDetail fromQuartzJobDetail(JobDetail jobDetail) {
    var jobDataMap = jobDetail.getJobDataMap();
    var rawUserId = jobDataMap.getString(USER_ID);
    var tenantId = jobDataMap.getString(TENANT);

    return ScheduledJobDetail.builder()
      .id(UUID.fromString(jobDetail.getKey().getName()))
      .tenantId(tenantId)
      .moduleName(moduleNameFromGroup(jobDetail.getKey().getGroup(), tenantId))
      .timerType(TimerType.fromValue(jobDataMap.getString(TIME_TYPE_DATA_FIELD)))
      .userId(isBlank(rawUserId) ? null : UUID.fromString(rawUserId))
      .build();
  }

  /**
   * Converts this instance into a Quartz {@link JobDetail} that runs {@link OkapiHttpRequestExecutor}.
   *
   * <p>
   * The timer id becomes the job key name and {@code <tenant>#<moduleName>} the job group; the tenant, timer type and
   * user id are stored in the job data map so they can be restored via {@link #fromQuartzJobDetail(JobDetail)} at
   * execution time.
   * </p>
   *
   * @return the Quartz {@link JobDetail} representing this timer
   */
  public JobDetail toQuartzJobDetail() {
    var jobBuilder = newJob(OkapiHttpRequestExecutor.class)
      .withIdentity(id.toString(), jobGroup(tenantId, moduleName))
      .usingJobData(TENANT, tenantId)
      .usingJobData(TIME_TYPE_DATA_FIELD, timerType.getValue());

    if (userId != null) {
      jobBuilder.usingJobData(USER_ID, userId.toString());
    }

    return jobBuilder.build();
  }

  private static String moduleNameFromGroup(String group, String tenantId) {
    if (group == null) {
      return null;
    }
    var prefix = tenantId + GROUP_SEPARATOR;
    return group.startsWith(prefix) ? group.substring(prefix.length()) : group;
  }
}
