package org.folio.scheduler.utils;

import static com.cronutils.mapper.CronMapper.fromUnixToQuartz;
import static com.cronutils.model.CronType.UNIX;
import static com.cronutils.model.definition.CronDefinitionBuilder.instanceDefinitionFor;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.cronutils.mapper.CronMapper;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.parser.CronParser;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

@Log4j2
@UtilityClass
public class CronUtils {

  private static final CronDefinition UNIX_DEF = instanceDefinitionFor(UNIX);
  private static final CronParser UNIX_PARSER = new CronParser(UNIX_DEF);
  private static final CronMapper UNIX_TO_QUARTZ_MAPPER = fromUnixToQuartz();

  public static String convertToQuartz(String cronExpression) {
    requireNonNull(cronExpression, "Cron expression cannot be null.");

    var parts = cronExpression.trim().split("\\s+");
    if (parts.length == 5) {
      var unixCron = UNIX_PARSER.parse(cronExpression);
      var quartzCron = UNIX_TO_QUARTZ_MAPPER.map(unixCron);
      var cron = quartzCron.asString();
      log.debug("Converted Unix cron expression '{}' to Quartz format '{}'", cronExpression, cron);
      return cron;
    } else if (parts.length == 6 || parts.length == 7) {
      return cronExpression;
    } else {
      log.warn("Invalid cron expression: {}. Must have 5 (Unix) or 6/7 (Quartz) fields.", cronExpression);
      throw new IllegalArgumentException(
        format("Invalid cron expression: %s. Must have 5 (Unix) or 6/7 (Quartz) fields.", cronExpression));
    }
  }
}
