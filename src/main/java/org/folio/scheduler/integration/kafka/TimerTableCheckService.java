package org.folio.scheduler.integration.kafka;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import java.sql.ResultSet;
import lombok.extern.log4j.Log4j2;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

@Log4j2
public class TimerTableCheckService {

  private static final String TIMER_TABLE_NAME = "timer";
  private static final String TABLE_EXIST_SQL = """
    SELECT EXISTS (
      SELECT 1 FROM information_schema.tables
        WHERE table_schema = ? AND table_name = ?)
    """;

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;
  private final TableNameCase tableNameCase;

  public TimerTableCheckService(JdbcTemplate jdbcTemplate, FolioExecutionContext context) {
    this(jdbcTemplate, context, TableNameCase.LOWER);
  }

  public TimerTableCheckService(JdbcTemplate jdbcTemplate, FolioExecutionContext context, TableNameCase tableNameCase) {
    this.jdbcTemplate = jdbcTemplate;
    this.context = context;
    this.tableNameCase = tableNameCase;
  }

  public boolean tableExists() {
    return tableExists(TIMER_TABLE_NAME);
  }

  private boolean tableExists(String tableName) {
    var schema = getDbSchemaName();
    var table = tableNameCase.format(tableName);

    log.info("Checking if table exists in schema: table = {}, schema = {}. Thread: {}", table, schema,
      Thread.currentThread().getName());

    var found = isTrue(jdbcTemplate.query(
      TABLE_EXIST_SQL,
      (ResultSet resultSet) -> resultSet.next() && resultSet.getBoolean(1),
      schema, table)
    );

    log.info("Table existence check result: table = {}, schema = {}, exists = {}. Thread: {}", table, schema,
      found, Thread.currentThread().getName());
    return found;
  }

  private String getDbSchemaName() {
    log.debug("FolioExecutionContext in use when getting DB schema name: tenantId = {}. Thread: {}",
      context.getTenantId(), Thread.currentThread().getName());
    return context.getFolioModuleMetadata().getDBSchemaName(context.getTenantId());
  }

  public enum TableNameCase {

    UPPER,
    LOWER,
    MIXED;

    private String format(String tableName) {
      return switch (this) {
        case UPPER -> tableName.toUpperCase();
        case LOWER -> tableName.toLowerCase();
        case MIXED -> tableName;
      };
    }
  }
}
