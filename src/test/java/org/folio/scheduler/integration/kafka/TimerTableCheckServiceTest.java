package org.folio.scheduler.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TimerTableCheckServiceTest {

  private static final String TIMER_TABLE_NAME = "timer";
  private static final String[] TABLE_TYPE = {"TABLE"};

  @Mock
  private DataSource dataSource;
  @Mock
  private Connection connection;
  @Mock
  private DatabaseMetaData databaseMetaData;
  @Mock
  private ResultSet resultSet;

  @InjectMocks
  private TimerTableCheckService timerTableCheckService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(dataSource, connection, databaseMetaData, resultSet);
  }

  @Test
  void tableExists_positive_tableIsPresent() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getTables(isNull(), isNull(), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE)))
      .thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);

    boolean result = timerTableCheckService.tableExists();

    assertThat(result).isTrue();
    verify(dataSource).getConnection();
    verify(connection).getMetaData();
    verify(connection).close();
    verify(databaseMetaData).getTables(isNull(), isNull(), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE));
    verify(resultSet).next();
    verify(resultSet).close();
  }

  @Test
  void tableExists_positive_tableIsAbsent() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getTables(isNull(), isNull(), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE)))
      .thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);

    boolean result = timerTableCheckService.tableExists();

    assertThat(result).isFalse();
    verify(dataSource).getConnection();
    verify(connection).getMetaData();
    verify(connection).close();
    verify(databaseMetaData).getTables(isNull(), isNull(), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE));
    verify(resultSet).next();
    verify(resultSet).close();
  }

  @Test
  void tableExists_negative_sqlExceptionWhenGettingConnection() throws SQLException {
    var expectedException = new SQLException("Failed to get connection");
    when(dataSource.getConnection()).thenThrow(expectedException);

    assertThatThrownBy(() -> timerTableCheckService.tableExists())
      .isInstanceOf(DataRetrievalFailureException.class)
      .hasMessageContaining("Failed to check if table " + TIMER_TABLE_NAME + " exists")
      .hasCause(expectedException);

    verify(dataSource).getConnection();
  }

  @Test
  void tableExists_negative_sqlExceptionWhenGettingMetaData() throws SQLException {
    var expectedException = new SQLException("Failed to get metadata");
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenThrow(expectedException);

    assertThatThrownBy(() -> timerTableCheckService.tableExists())
      .isInstanceOf(DataRetrievalFailureException.class)
      .hasMessageContaining("Failed to check if table " + TIMER_TABLE_NAME + " exists")
      .hasCause(expectedException);

    verify(dataSource).getConnection();
    verify(connection).getMetaData();
    verify(connection).close();
  }

  @Test
  void tableExists_negative_sqlExceptionWhenGettingTables() throws SQLException {
    var expectedException = new SQLException("Failed to get tables");
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getTables(isNull(), isNull(), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE)))
      .thenThrow(expectedException);

    assertThatThrownBy(() -> timerTableCheckService.tableExists())
      .isInstanceOf(DataRetrievalFailureException.class)
      .hasMessageContaining("Failed to check if table " + TIMER_TABLE_NAME + " exists")
      .hasCause(expectedException);

    verify(dataSource).getConnection();
    verify(connection).getMetaData();
    verify(connection).close();
    verify(databaseMetaData).getTables(isNull(), isNull(), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE));
  }

  @Test
  void tableExists_negative_sqlExceptionWhenCheckingResultSet() throws SQLException {
    var expectedException = new SQLException("Failed to check result set");
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getTables(isNull(), isNull(), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE)))
      .thenReturn(resultSet);
    when(resultSet.next()).thenThrow(expectedException);

    assertThatThrownBy(() -> timerTableCheckService.tableExists())
      .isInstanceOf(DataRetrievalFailureException.class)
      .hasMessageContaining("Failed to check if table " + TIMER_TABLE_NAME + " exists")
      .hasCause(expectedException);

    verify(dataSource).getConnection();
    verify(connection).getMetaData();
    verify(connection).close();
    verify(databaseMetaData).getTables(isNull(), isNull(), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE));
    verify(resultSet).next();
    verify(resultSet).close();
  }
}
