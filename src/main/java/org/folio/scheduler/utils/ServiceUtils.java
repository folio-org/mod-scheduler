package org.folio.scheduler.utils;

import static org.apache.commons.lang3.StringUtils.defaultString;

import lombok.experimental.UtilityClass;
import org.springframework.dao.DataAccessException;

@UtilityClass
public class ServiceUtils {

  // SQL state code for "undefined table" error in PostgreSQL
  // see https://www.postgresql.org/docs/current/errcodes-appendix.html
  private static final String SQLSTATE_UNDEFINED_TABLE = "42P01";
  private static final String TIMER_TABLE_NAME = "timer";
  private static final String TIMER_TABLE_MISSING_MSG = "relation \"" + TIMER_TABLE_NAME + "\" does not exist";

  public static boolean isTimerTableMissing(DataAccessException e) {
    return defaultString(e.getMessage()).contains(TIMER_TABLE_MISSING_MSG);
  }
}
