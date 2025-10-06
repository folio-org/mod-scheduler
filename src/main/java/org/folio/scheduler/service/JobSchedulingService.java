package org.folio.scheduler.service;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.Map.entry;
import static java.util.TimeZone.getTimeZone;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.math.NumberUtils.createLong;
import static org.folio.scheduler.domain.dto.TimerUnit.DAY;
import static org.folio.scheduler.domain.dto.TimerUnit.HOUR;
import static org.folio.scheduler.domain.dto.TimerUnit.MILLISECOND;
import static org.folio.scheduler.domain.dto.TimerUnit.MINUTE;
import static org.folio.scheduler.domain.dto.TimerUnit.SECOND;
import static org.folio.scheduler.utils.CronUtils.convertToQuartz;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.JobKey.jobKey;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.TriggerKey.triggerKey;

import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerUnit;
import org.folio.scheduler.exception.TimerSchedulingException;
import org.folio.scheduler.service.jobs.OkapiHttpRequestExecutor;
import org.folio.scheduler.utils.Validate;
import org.folio.spring.FolioExecutionContext;
import org.quartz.CronTrigger;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class JobSchedulingService {

  private final Scheduler scheduler;
  private final FolioExecutionContext folioExecutionContext;

  /**
   * Contains multiplication value to convert request delay to milliseconds.
   */
  private final Map<TimerUnit, Long> timerUnitFactorMap = Map.ofEntries(
    entry(MILLISECOND, ofMillis(1).toMillis()),
    entry(SECOND, ofSeconds(1).toMillis()),
    entry(MINUTE, ofMinutes(1).toMillis()),
    entry(HOUR, ofHours(1).toMillis()),
    entry(DAY, ofDays(1).toMillis()));

  /**
   * Schedules recurring job.
   *
   * @param timerDescriptor - recurring job descriptor
   */
  @Transactional
  public boolean schedule(TimerDescriptor timerDescriptor) {
    if (isTriggerDisabled(timerDescriptor)) {
      log.info("Recurring job is disabled, it will not be scheduled. [timerId: {}]", timerDescriptor.getId());
      return false;
    }

    var scheduledTask = newJob(OkapiHttpRequestExecutor.class)
      .withIdentity(timerDescriptor.getId().toString())
      .usingJobData(TENANT, folioExecutionContext.getTenantId())
      .usingJobData(USER_ID, folioExecutionContext.getUserId().toString())
      .build();

    try {
      scheduler.scheduleJob(scheduledTask, getTrigger(timerDescriptor));
    } catch (ObjectAlreadyExistsException alreadyExistsException) {
      return false;
    } catch (SchedulerException exception) {
      log.error("Failed to schedule job [jobId: {}] : {}", timerDescriptor.getId(), exception.getMessage());
      throw new TimerSchedulingException("Failed to schedule job", exception);
    }
    return true;
  }

  /**
   * Re-schedules recurring job.
   *
   * <p>
   * This method only changes the trigger for existing task.
   * </p>
   *
   * @param timerDescriptor - recurring job descriptor
   */
  @Transactional
  public void reschedule(TimerDescriptor oldTimerDescriptor, TimerDescriptor timerDescriptor) {
    try {
      rescheduleJob(oldTimerDescriptor, timerDescriptor);
    } catch (SchedulerException exception) {
      log.error("Failed to reschedule job [jobId: {}] : {}", timerDescriptor.getId(), exception.getMessage());
      throw new TimerSchedulingException("Failed to reschedule job", exception);
    }
  }

  /**
   * Deletes recurring job.
   *
   * @param timerDescriptor - recurring job descriptor
   */
  @Transactional
  public void delete(TimerDescriptor timerDescriptor) {
    if (isFalse(timerDescriptor.getEnabled())) {
      log.debug("Timer descriptor is disabled, nothing to delete [jobId: {}]", timerDescriptor.getId());
      return;
    }

    try {
      scheduler.deleteJob(jobKey(timerDescriptor.getId().toString()));
    } catch (SchedulerException exception) {
      log.error("Failed to delete job [jobId: {}] : {}", timerDescriptor.getId(), exception.getMessage());
      throw new TimerSchedulingException("Failed to delete job", exception);
    }
  }

  private void rescheduleJob(TimerDescriptor oldDesc, TimerDescriptor newDesc) throws SchedulerException {
    if (isTriggerDisabled(newDesc)) {
      deleteRecurringJobIfPresent(oldDesc);
      return;
    }

    var timerId = newDesc.getId().toString();
    if (isTimerNotUpdated(oldDesc, newDesc)) {
      log.info("Recurring job trigger is not updated [timerId: {}]", timerId);
      return;
    }

    scheduler.rescheduleJob(triggerKey(timerId), getTrigger(newDesc));
  }

  private void deleteRecurringJobIfPresent(TimerDescriptor prevTimerDesc) throws SchedulerException {
    var timerId = prevTimerDesc.getId();
    if (isTriggerDisabled(prevTimerDesc)) {
      log.info("Previous and current triggers are disabled, ignoring this call [timerId: {}]", timerId);
      return;
    }

    scheduler.deleteJob(jobKey(timerId.toString()));
    log.info("Recurring job be deleted, timer is disabled [timerId: {}]", timerId);
  }

  private Trigger getTrigger(TimerDescriptor timerDescriptor) {
    var routingEntry = timerDescriptor.getRoutingEntry();
    var isNonNullDelay = routingEntry.getDelay() != null && routingEntry.getUnit() != null;
    var isNonNullSchedule = routingEntry.getSchedule() != null;

    Validate.isTrue(isNonNullDelay || isNonNullSchedule, () -> "Recurring job trigger is not specified");
    Validate.isTrue(!(isNonNullDelay && isNonNullSchedule), () ->
      "Recurring job cannot have specified delay and schedule at the same time");

    return routingEntry.getSchedule() != null
      ? getCronTrigger(timerDescriptor)
      : getForeverRepeatingTrigger(timerDescriptor);
  }

  private Trigger getForeverRepeatingTrigger(TimerDescriptor timerDescriptor) {
    var re = timerDescriptor.getRoutingEntry();
    var timerId = timerDescriptor.getId().toString();
    var repeatInterval = timerUnitFactorMap.get(re.getUnit()) * Long.parseLong(re.getDelay());
    Validate.isTrue(repeatInterval >= 1000L, () -> "Repeat interval must be greater than 1 second.");
    return newTrigger()
      .withIdentity(triggerKey(timerId))
      .withSchedule(simpleSchedule().repeatForever().withIntervalInMilliseconds(repeatInterval))
      .forJob(jobKey(timerId))
      .build();
  }

  private CronTrigger getCronTrigger(TimerDescriptor timerDescriptor) {
    var timerId = timerDescriptor.getId().toString();
    var schedule = timerDescriptor.getRoutingEntry().getSchedule();
    var timeZone = defaultIfNull(schedule.getZone(), "UTC");
    var cron = schedule.getCron();
    var cronExpression = convertToQuartz(cron);
    return newTrigger()
      .withIdentity(triggerKey(timerId))
      .withSchedule(cronSchedule(cronExpression).inTimeZone(getTimeZone(timeZone)))
      .forJob(jobKey(timerId))
      .build();
  }

  private static boolean isTriggerDisabled(TimerDescriptor desc) {
    return isFalse(desc.getEnabled())
      || desc.getRoutingEntry() == null
      || Objects.equals(createLong(desc.getRoutingEntry().getDelay()), 0L);
  }

  private static boolean isTimerNotUpdated(TimerDescriptor oldValue, TimerDescriptor newValue) {
    var oldRe = oldValue.getRoutingEntry();
    var newRe = newValue.getRoutingEntry();
    return Objects.equals(oldRe.getSchedule(), newRe.getSchedule())
      && Objects.equals(oldRe.getDelay(), newRe.getDelay()) && Objects.equals(oldRe.getUnit(), newRe.getUnit());
  }
}
