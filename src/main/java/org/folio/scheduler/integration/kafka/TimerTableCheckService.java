package org.folio.scheduler.integration.kafka;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.folio.spring.FolioExecutionContext;
import org.springframework.dao.DataRetrievalFailureException;

@Log4j2
public class TimerTableCheckService {

  private static final String[] TABLE_TYPE = {"TABLE"};
  private static final String TIMER_TABLE_NAME = "timer";

  private final DataSource dataSource;
  private final FolioExecutionContext context;
  private final TableNameCase tableNameCase;

  public TimerTableCheckService(DataSource dataSource, FolioExecutionContext context) {
    this(dataSource, context, TableNameCase.LOWER);
  }

  public TimerTableCheckService(DataSource dataSource, FolioExecutionContext context, TableNameCase tableNameCase) {
    this.dataSource = dataSource;
    this.context = context;
    this.tableNameCase = tableNameCase;
  }

  public boolean tableExists() {
    return tableExists(TIMER_TABLE_NAME);
  }

  private boolean tableExists(String tableName) {
    try {
      try (Connection connection = dataSource.getConnection()) {
        DatabaseMetaData metaData = connection.getMetaData();

        var schema = getDbSchemaName();
        var table = tableNameCase.format(tableName);
        log.debug("Checking if table exists in schema: table = {}, schema = {}. Thread: {}", table, schema,
          Thread.currentThread().getName());
        try (ResultSet resultSet = metaData.getTables(null, schema, table, TABLE_TYPE)) {
          return resultSet.next();
        }
      }
    } catch (SQLException e) {
      throw new DataRetrievalFailureException("Failed to check if table " + tableName + " exists", e);
    }
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
