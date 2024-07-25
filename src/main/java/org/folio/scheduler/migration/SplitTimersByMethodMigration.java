package org.folio.scheduler.migration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import liquibase.database.Database;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.repository.SchedulerTimerRepository;
import org.folio.scheduler.service.JobSchedulingService;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
public class SplitTimersByMethodMigration extends AbstractCustomTaskChangeMigration {

  protected static final String QUERY_TIMERS_WITH_MULTIPLE_METHODS =
    "SELECT id FROM timer WHERE jsonb_array_length(timer_descriptor -> 'routingEntry' -> 'methods') > 1";

  @Override
  @Transactional
  public void execute(Database database) {
    var idsOfTimersToSplit = new HashSet<String>();
    runQuery(database, QUERY_TIMERS_WITH_MULTIPLE_METHODS,
      resultSet -> idsOfTimersToSplit.add(resultSet.getString("id")));

    if (!idsOfTimersToSplit.isEmpty()) {
      log.info("Found {} timers with multiple HTTP methods - splitting", idsOfTimersToSplit.size());
      var jobSchedulingService = springApplicationContext.getBean(JobSchedulingService.class);
      var schedulerTimerRepository = springApplicationContext.getBean(SchedulerTimerRepository.class);
      for (String timerId : idsOfTimersToSplit) {
        var uuid = UUID.fromString(timerId);
        var byId = schedulerTimerRepository.findById(uuid);
        byId.ifPresent(
          timerDescriptorEntity -> splitTimer(timerDescriptorEntity, schedulerTimerRepository, jobSchedulingService));
      }
    }
  }

  private static void splitTimer(TimerDescriptorEntity timer, SchedulerTimerRepository schedulerTimerRepository,
    JobSchedulingService jobSchedulingService) {
    log.info("Splitting timer {}", timer.getId());
    var timerRoutingEntryHttpMethods = new ArrayList<>(timer.getTimerDescriptor().getRoutingEntry().getMethods());
    timer.getTimerDescriptor().getRoutingEntry().setMethods(List.of(timerRoutingEntryHttpMethods.get(0)));
    schedulerTimerRepository.save(timer);
    timerRoutingEntryHttpMethods.remove(0);
    var newTimers = timerRoutingEntryHttpMethods.stream()
      .map(httpMethod -> createNewTimer(timer, schedulerTimerRepository, httpMethod)).toList();
    var timerEnabled = timer.getTimerDescriptor().getEnabled() != null && timer.getTimerDescriptor().getEnabled();
    if (timerEnabled) {
      log.info("Scheduling jobs for timers {} for methods {}", newTimers.stream().map(TimerDescriptorEntity::getId),
        timerRoutingEntryHttpMethods);
      newTimers.stream().map(TimerDescriptorEntity::getTimerDescriptor).forEach(jobSchedulingService::schedule);
    }
  }

  private static @NotNull TimerDescriptorEntity createNewTimer(TimerDescriptorEntity timer,
    SchedulerTimerRepository schedulerTimerRepository, String httpMethod) {
    UUID newId = UUID.randomUUID();
    timer.setId(newId);
    timer.getTimerDescriptor().setId(newId);
    timer.getTimerDescriptor().getRoutingEntry().setMethods(List.of(httpMethod));
    return schedulerTimerRepository.save(timer);
  }
}
