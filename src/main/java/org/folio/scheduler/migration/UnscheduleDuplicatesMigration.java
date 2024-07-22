package org.folio.scheduler.migration;

import java.util.HashSet;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.integration.spring.SpringResourceAccessor;
import liquibase.resource.ResourceAccessor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.context.ApplicationContext;

@Log4j2
public class UnscheduleDuplicatesMigration implements CustomTaskChange {

  private ApplicationContext springApplicationContext;

  @Override
  public void execute(Database database) throws CustomChangeException {
    JdbcConnection connection = (JdbcConnection) database.getConnection();
    try (var statement = connection.getWrappedConnection().prepareStatement("""
      SELECT
        id
      FROM (
        SELECT
      	  id,
      	  row_number() over (partition by natural_key order by id) as rn
        FROM
      	  timer
        WHERE timer_descriptor->'enabled' = 'true'
      )
      WHERE
      	rn > 1
      """)) {
      var resultSet = statement.executeQuery();
      var idsOfTimersToUnschedule = new HashSet<String>();
      while (resultSet.next()) {
        String timerId = resultSet.getString("id");
        idsOfTimersToUnschedule.add(timerId);
      }

      if (!idsOfTimersToUnschedule.isEmpty()) {
        log.info("Found {} duplicate timers - unscheduling", idsOfTimersToUnschedule.size());
        var scheduler = springApplicationContext.getBean(Scheduler.class);
        idsOfTimersToUnschedule.forEach(timerId -> {
          try {
            log.info("Unscheduling timer {}", timerId);
            scheduler.deleteJob(JobKey.jobKey(timerId));
          } catch (SchedulerException e) {
            log.error("Failed to unschedule timer {}", timerId, e);
          }
        });
      }

    } catch (Exception e) {
      throw new RuntimeException("Failed to execute migration " + this.getClass().getSimpleName(), e);
    }
  }

  @Override
  public String getConfirmationMessage() {
    return "Completed " + this.getClass().getSimpleName();
  }

  @Override
  public void setUp() throws SetupException {
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
