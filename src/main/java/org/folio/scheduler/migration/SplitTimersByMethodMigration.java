package org.folio.scheduler.migration;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.ValidationErrors;
import liquibase.integration.spring.SpringResourceAccessor;
import liquibase.resource.ResourceAccessor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.repository.SchedulerTimerRepository;
import org.folio.scheduler.service.JobSchedulingService;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
public class SplitTimersByMethodMigration implements CustomTaskChange {

  private ApplicationContext springApplicationContext;

  @Override
  @Transactional
  public void execute(Database database) {
    JdbcConnection connection = (JdbcConnection) database.getConnection();
    var idsOfTimersToSplit = new HashSet<String>();
    try (var statement = connection.getWrappedConnection().prepareStatement(
      "SELECT id FROM timer WHERE jsonb_array_length(timer_descriptor -> 'routingEntry' -> 'methods') > 1")) {
      var resultSet = statement.executeQuery();
      while (resultSet.next()) {
        var timerId = resultSet.getString("id");
        idsOfTimersToSplit.add(timerId);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute migration " + this.getClass().getSimpleName(), e);
    }

    if (!idsOfTimersToSplit.isEmpty()) {
      log.info("Found {} timers with multiple HTTP methods - splitting", idsOfTimersToSplit.size());
      var jobSchedulingService = springApplicationContext.getBean(JobSchedulingService.class);
      var schedulerTimerRepository = springApplicationContext.getBean(SchedulerTimerRepository.class);
      for (String timerId : idsOfTimersToSplit) {
        var uuid = UUID.fromString(timerId);
        var byId = schedulerTimerRepository.findById(uuid);
        if (byId.isPresent()) {
          TimerDescriptorEntity timer = byId.get();
          log.info("Splitting timer {}", timer.getId());
          var timerRoutingEntryHttpMethods = timer.getTimerDescriptor().getRoutingEntry().getMethods();
          timer.getTimerDescriptor().getRoutingEntry().setMethods(List.of(timerRoutingEntryHttpMethods.get(0)));
          schedulerTimerRepository.save(timer);
          timerRoutingEntryHttpMethods.remove(0);
          var newTimers = timerRoutingEntryHttpMethods.stream().map(httpMethod -> {
            UUID newId = UUID.randomUUID();
            timer.setId(newId);
            timer.getTimerDescriptor().setId(newId);
            timer.getTimerDescriptor().getRoutingEntry().setMethods(List.of(httpMethod));
            return schedulerTimerRepository.save(timer);
          }).toList();
          var timerEnabled = timer.getTimerDescriptor().getEnabled() != null && timer.getTimerDescriptor().getEnabled();
          if (timerEnabled) {
            log.info("Scheduling jobs for timers {} for methods {}",
              newTimers.stream().map(TimerDescriptorEntity::getId), timerRoutingEntryHttpMethods);
            newTimers.stream().map(TimerDescriptorEntity::getTimerDescriptor).forEach(jobSchedulingService::schedule);
          }
        }
      }
    }
  }

  @Override
  public String getConfirmationMessage() {
    return "Completed " + this.getClass().getSimpleName();
  }

  @Override
  public void setUp() {
    // Do nothing
  }

  @Override
  public void setFileOpener(ResourceAccessor resourceAccessor) {
    try {
      var springResourceAccessor = (SpringResourceAccessor) resourceAccessor;
      springApplicationContext =
        (ApplicationContext) FieldUtils.readField(springResourceAccessor, "resourceLoader", true);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Failed to obtain Spring Application Context", e);
    }
  }

  @Override
  public ValidationErrors validate(Database database) {
    return null;
  }
}
