package org.folio.scheduler.migration;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
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
 * Moves every enabled timer's Quartz job and trigger out of the legacy {@code DEFAULT} group into the tenant/module
 * aware group {@code <tenant>#<moduleName>}.
 *
 * <p>
 * Jobs scheduled by the previous code all live in Quartz's {@code DEFAULT} group. Quartz has no "change group"
 * operation, so for each enabled timer the {@code DEFAULT}-group job is deleted and the timer is rescheduled from its
 * descriptor via {@link JobSchedulingService#schedule}, which now assigns the {@code <tenant>#<moduleName>} group. USER
 * timers that still lack a user id are skipped (they cannot be scheduled). The migration is idempotent: a missing
 * {@code DEFAULT} job makes the delete a no-op, and an already-regrouped job makes the reschedule a no-op.
 * </p>
 */
@Log4j2
public class RegroupTimerJobsMigration extends AbstractCustomTaskChangeMigration {

  private static final String SELECT_ENABLED_TIMER_IDS =
    "SELECT id FROM timer WHERE timer_descriptor->'enabled' = 'true'";

  @Override
  @Transactional
  public void execute(Database database) {
    var enabledTimerIds = new ArrayList<String>();
    runQuery(database, SELECT_ENABLED_TIMER_IDS, resultSet -> enabledTimerIds.add(resultSet.getString("id")));

    if (enabledTimerIds.isEmpty()) {
      log.info("No enabled timers found - nothing to regroup");
      return;
    }

    var context = buildContext();
    if (isBlank(context.tenantId())) {
      throw new MigrationException("Cannot regroup timers: tenant id is missing in the execution context", null);
    }

    log.info("Regrouping {} enabled timer(s) into the tenant#moduleName Quartz group [tenant: {}]",
      enabledTimerIds.size(), context.tenantId());

    regroupTimers(enabledTimerIds, context);
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

  private void regroupTimers(List<String> enabledTimerIds, MigrationContext context) {
    for (var id : enabledTimerIds) {
      var timerId = UUID.fromString(id);
      context.repository().findById(timerId).ifPresentOrElse(
        entity -> regroupTimer(context.mapper().toDescriptor(entity), context),
        () -> log.warn("Enabled timer not found by id, skipping [timerId: {}]", timerId));
    }
  }

  private void regroupTimer(TimerDescriptor descriptor, MigrationContext context) {
    var timerId = descriptor.getId();
    if (descriptor.getType() == TimerType.USER && descriptor.getUserId() == null) {
      log.warn("Skipping regroup of user timer without a user id [timerId: {}]", timerId);
      return;
    }

    deleteDefaultGroupJob(context.scheduler(), timerId);
    scheduleWithContext(context, descriptor);
    log.info("Regrouped timer [timerId: {}, type: {}]", timerId, descriptor.getType());
  }

  private static void scheduleWithContext(MigrationContext context, TimerDescriptor descriptor) {
    try (var ignored = new FolioExecutionContextSetter(context.moduleMetadata(), buildHeaders(context.tenantId()))) {
      context.jobSchedulingService().schedule(descriptor);
    }
  }

  private static void deleteDefaultGroupJob(Scheduler scheduler, UUID timerId) {
    try {
      scheduler.deleteJob(jobKey(timerId.toString()));
      log.info("Deleted existing scheduled job in the DEFAULT group for timer [timerId: {}]", timerId);
    } catch (SchedulerException e) {
      throw new MigrationException("Failed to delete existing scheduled job for timer " + timerId, e);
    }
  }

  private static Map<String, Collection<String>> buildHeaders(String tenantId) {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(TENANT, List.of(tenantId));
    return headers;
  }

  private record MigrationContext(TimerDescriptorMapper mapper, SchedulerTimerRepository repository,
    JobSchedulingService jobSchedulingService, Scheduler scheduler, FolioModuleMetadata moduleMetadata,
    String tenantId) {}
}
