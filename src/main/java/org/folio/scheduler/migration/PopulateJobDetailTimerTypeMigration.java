package org.folio.scheduler.migration;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.quartz.JobKey.jobKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import liquibase.database.Database;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.exception.MigrationException;
import org.folio.scheduler.repository.SchedulerTimerRepository;
import org.folio.scheduler.service.JobSchedulingService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recreates every enabled timer's Quartz job so that its job detail carries the newly introduced {@code timer-type}
 * field and system timers no longer keep a stale user id.
 *
 * <p>
 * Existing jobs were scheduled by the previous code, so their job details lack {@code timer-type} and system timers may
 * still carry the user id of whoever originally scheduled them. For each enabled timer the original user id is read from
 * the current job detail, the job is deleted and then rescheduled from the timer descriptor via
 * {@link JobSchedulingService#schedule}. The reschedule runs inside a {@link FolioExecutionContextSetter} that supplies
 * the tenant and - for USER timers only - the preserved user id, so USER timers keep their owner while SYSTEM timers are
 * rescheduled without a user id (the cleanup this migration performs).
 * </p>
 */
@Log4j2
public class PopulateJobDetailTimerTypeMigration extends AbstractCustomTaskChangeMigration {

  private static final String SELECT_ENABLED_TIMER_IDS =
    "SELECT id FROM timer WHERE timer_descriptor->'enabled' = 'true'";

  @Override
  @Transactional
  public void execute(Database database) {
    var enabledTimerIds = new ArrayList<String>();
    runQuery(database, SELECT_ENABLED_TIMER_IDS, resultSet -> enabledTimerIds.add(resultSet.getString("id")));

    if (enabledTimerIds.isEmpty()) {
      log.info("No enabled timers found - nothing to recreate");
      return;
    }

    var repository = springApplicationContext.getBean(SchedulerTimerRepository.class);
    var jobSchedulingService = springApplicationContext.getBean(JobSchedulingService.class);
    var scheduler = springApplicationContext.getBean(Scheduler.class);
    var moduleMetadata = springApplicationContext.getBean(FolioModuleMetadata.class);
    var tenantId = springApplicationContext.getBean(FolioExecutionContext.class).getTenantId();

    if (isBlank(tenantId)) {
      throw new MigrationException("Cannot recreate timers: tenant id is missing in the execution context", null);
    }

    log.info("Recreating {} enabled timer(s) to populate timer type in job details and clean up user ids of "
      + "system timers [tenant: {}]", enabledTimerIds.size(), tenantId);

    for (var id : enabledTimerIds) {
      var timerId = UUID.fromString(id);
      repository.findById(timerId).ifPresentOrElse(
        entity -> recreateTimer(entity, tenantId, jobSchedulingService, scheduler, moduleMetadata),
        () -> log.warn("Enabled timer not found by id, skipping [timerId: {}]", timerId));
    }
  }

  private void recreateTimer(TimerDescriptorEntity entity, String tenantId, JobSchedulingService jobSchedulingService,
    Scheduler scheduler, FolioModuleMetadata moduleMetadata) {
    var timerId = entity.getId();
    var descriptor = entity.getTimerDescriptor();
    var type = resolveType(entity);
    if (descriptor.getId() == null) {
      descriptor.setId(timerId);
    }
    if (descriptor.getType() == null) {
      descriptor.setType(type);
    }

    UUID userId = null;
    if (type == TimerType.USER) {
      userId = readOriginalUserId(scheduler, timerId);
      if (userId == null) {
        log.error("Skipping user timer recreation: original user id could not be resolved from the existing "
          + "job detail [timerId: {}]", timerId);
        return;
      }
    }

    deleteExistingJob(scheduler, timerId);

    try (var ignored = new FolioExecutionContextSetter(moduleMetadata, buildHeaders(tenantId, userId))) {
      jobSchedulingService.schedule(descriptor);
    }

    log.info("Recreated timer [timerId: {}, type: {}, userIdPreserved: {}]", timerId, type, userId != null);
  }

  private static UUID readOriginalUserId(Scheduler scheduler, UUID timerId) {
    try {
      var jobDetail = scheduler.getJobDetail(jobKey(timerId.toString()));
      if (jobDetail == null) {
        log.warn("No existing scheduled job found for user timer [timerId: {}]", timerId);
        return null;
      }
      var storedUserId = jobDetail.getJobDataMap().getString(USER_ID);
      return isBlank(storedUserId) ? null : UUID.fromString(storedUserId);
    } catch (SchedulerException e) {
      throw new MigrationException("Failed to read existing job detail for timer " + timerId, e);
    }
  }

  private static void deleteExistingJob(Scheduler scheduler, UUID timerId) {
    try {
      scheduler.deleteJob(jobKey(timerId.toString()));
    } catch (SchedulerException e) {
      throw new MigrationException("Failed to delete existing scheduled job for timer " + timerId, e);
    }
  }

  private static Map<String, Collection<String>> buildHeaders(String tenantId, UUID userId) {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(TENANT, List.of(tenantId));
    if (userId != null) {
      headers.put(USER_ID, List.of(userId.toString()));
    }
    return headers;
  }

  private static TimerType resolveType(TimerDescriptorEntity entity) {
    var descriptorType = entity.getTimerDescriptor().getType();
    if (descriptorType != null) {
      return descriptorType;
    }
    return switch (entity.getType()) {
      case USER -> TimerType.USER;
      case SYSTEM -> TimerType.SYSTEM;
    };
  }
}