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
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.exception.MigrationException;
import org.folio.scheduler.mapper.TimerDescriptorMapper;
import org.folio.scheduler.repository.SchedulerTimerRepository;
import org.folio.scheduler.service.JobSchedulingService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recreates every enabled timer's Quartz job and backfills the newly added {@code user_id} column, so that each job
 * detail carries the {@code timer-type} field and the correct user id.
 *
 * <p>
 * Jobs scheduled by the previous code lack {@code timer-type} in their job details, SYSTEM timers may still carry the
 * user id of whoever originally scheduled them, and the {@code user_id} column is empty for every timer. For each
 * enabled timer the original user id is read from the current job detail (USER timers only) and persisted into the
 * {@code user_id} column; the timer is then rescheduled from a descriptor rebuilt from the entity, so the fresh job
 * detail carries the correct type and user id. SYSTEM timers are recreated without a user id (in both the column and
 * the job detail), which is the stale-user-id cleanup this migration performs.
 * </p>
 */
@Log4j2
public class PopulateTimerTypeAndUserIdMigration extends AbstractCustomTaskChangeMigration {

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

    var context = buildContext();
    if (isBlank(context.tenantId())) {
      throw new MigrationException("Cannot recreate timers: tenant id is missing in the execution context", null);
    }

    log.info("Recreating {} enabled timer(s) to populate timer type in job details, backfill user id, and clear user "
      + "ids of system timers [tenant: {}]", enabledTimerIds.size(), context.tenantId());

    recreateTimers(enabledTimerIds, context);
  }

  private MigrationContext buildContext() {
    return new MigrationContext(
      springApplicationContext.getBean(TimerDescriptorMapper.class),
      springApplicationContext.getBean(SchedulerTimerRepository.class),
      springApplicationContext.getBean(JobSchedulingService.class),
      springApplicationContext.getBean(Scheduler.class),
      springApplicationContext.getBean(FolioModuleMetadata.class),
      springApplicationContext.getBean(FolioExecutionContext.class).getTenantId());
  }

  private void recreateTimers(List<String> enabledTimerIds, MigrationContext context) {
    for (var id : enabledTimerIds) {
      var timerId = UUID.fromString(id);
      context.repository().findById(timerId).ifPresentOrElse(
        entity -> recreateTimer(entity, context),
        () -> log.warn("Enabled timer not found by id, skipping [timerId: {}]", timerId));
    }
  }

  private void recreateTimer(TimerDescriptorEntity entity, MigrationContext context) {
    var timerId = entity.getId();
    var type = resolveType(entity);

    UUID userId = null;
    if (type == TimerType.USER) {
      userId = readOriginalUserId(context.scheduler(), timerId);
      if (userId == null) {
        log.error("Skipping user timer recreation: original user id could not be resolved from the existing "
          + "job detail [timerId: {}]", timerId);
        return;
      }
    }

    entity.setUserId(userId);
    context.repository().save(entity);

    var descriptor = context.mapper().toDescriptor(entity);
    deleteExistingJob(context.scheduler(), timerId);
    scheduleWithContext(context, descriptor);
    log.info("Recreated timer [timerId: {}, type: {}, userIdPreserved: {}]", timerId, type, userId != null);
  }

  private static void scheduleWithContext(MigrationContext context, TimerDescriptor descriptor) {
    try (var ignored = new FolioExecutionContextSetter(context.moduleMetadata(), buildHeaders(context.tenantId()))) {
      context.jobSchedulingService().schedule(descriptor);
    }
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

  private static Map<String, Collection<String>> buildHeaders(String tenantId) {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(TENANT, List.of(tenantId));
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

  private record MigrationContext(TimerDescriptorMapper mapper, SchedulerTimerRepository repository,
    JobSchedulingService jobSchedulingService, Scheduler scheduler, FolioModuleMetadata moduleMetadata,
    String tenantId) {}
}
