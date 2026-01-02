package org.folio.scheduler.integration.kafka;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TimerTableCheckService {

  private static final String[] TABLE_TYPE = {"TABLE"};
  private static final String TIMER_TABLE_NAME = "timer";

  private final DataSource dataSource;
  private final TableNameCase tableNameCase = TableNameCase.LOWER;

  public boolean tableExists() {
    return tableExists(TIMER_TABLE_NAME);
  }

  private boolean tableExists(String tableName) {
    try {
      try (Connection connection = dataSource.getConnection()) {
        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet resultSet = metaData.getTables(null, null, tableNameCase.format(tableName), TABLE_TYPE)) {
          return resultSet.next();
        }
      }
    } catch (SQLException e) {
      throw new DataRetrievalFailureException("Failed to check if table " + tableName + " exists", e);
    }
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
