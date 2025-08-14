package org.folio.scheduler.migration;

import java.util.HashSet;
import liquibase.database.Database;
import lombok.extern.log4j.Log4j2;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
public class UnscheduleDuplicatesMigration extends AbstractCustomTaskChangeMigration {

  private static final String SELECT_DUPLICATE_TIMERS = """
    SELECT
      id
    FROM (
      SELECT
        id,
        row_number() over (partition by natural_key order by id) as rn
      FROM
        timer
      WHERE timer_descriptor->'enabled' = 'true'
    ) AS tmp
    WHERE
      rn > 1
    """;

  @Override
  @Transactional
  public void execute(Database database) {
    var idsOfTimersToUnschedule = new HashSet<String>();
    runQuery(database, SELECT_DUPLICATE_TIMERS, resultSet -> idsOfTimersToUnschedule.add(resultSet.getString("id")));

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
  }
}
