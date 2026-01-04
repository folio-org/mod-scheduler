package org.folio.scheduler.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
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
import org.folio.scheduler.integration.kafka.TimerTableCheckService.TableNameCase;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TimerTableCheckServiceTest {

  private static final String TIMER_TABLE_NAME = "timer";
  private static final String[] TABLE_TYPE = {"TABLE"};
  private static final String SCHEMA_NAME = TENANT_ID + "_mod_scheduler";

  @Mock
  private DataSource dataSource;
  @Mock
  private Connection connection;
  @Mock
  private DatabaseMetaData databaseMetaData;
  @Mock
  private ResultSet resultSet;
  @Mock
  private FolioExecutionContext context;
  @Mock
  private FolioModuleMetadata moduleMetadata;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(dataSource, connection, databaseMetaData, resultSet);
  }

  @Test
  void tableExists_positive_tableIsPresent() throws SQLException {
    setupContextMocks();
    var timerTableCheckService = new TimerTableCheckService(dataSource, context);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getTables(isNull(), eq(SCHEMA_NAME), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE)))
      .thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);

    boolean result = timerTableCheckService.tableExists();

    assertThat(result).isTrue();
    verify(connection).close();
    verify(resultSet).close();
  }

  @Test
  void tableExists_positive_tableIsAbsent() throws SQLException {
    setupContextMocks();
    var timerTableCheckService = new TimerTableCheckService(dataSource, context);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getTables(isNull(), eq(SCHEMA_NAME), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE)))
      .thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);

    boolean result = timerTableCheckService.tableExists();

    assertThat(result).isFalse();
    verify(connection).close();
    verify(resultSet).close();
  }

  @Test
  void tableExists_negative_sqlExceptionWhenGettingConnection() throws SQLException {
    var timerTableCheckService = new TimerTableCheckService(dataSource, context);

    var expectedException = new SQLException("Failed to get connection");
    when(dataSource.getConnection()).thenThrow(expectedException);

    assertThatThrownBy(timerTableCheckService::tableExists)
      .isInstanceOf(DataRetrievalFailureException.class)
      .hasMessageContaining("Failed to check if table " + TIMER_TABLE_NAME + " exists")
      .hasCause(expectedException);
  }

  @Test
  void tableExists_negative_sqlExceptionWhenGettingMetaData() throws SQLException {
    var timerTableCheckService = new TimerTableCheckService(dataSource, context);

    var expectedException = new SQLException("Failed to get metadata");
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenThrow(expectedException);

    assertThatThrownBy(timerTableCheckService::tableExists)
      .isInstanceOf(DataRetrievalFailureException.class)
      .hasMessageContaining("Failed to check if table " + TIMER_TABLE_NAME + " exists")
      .hasCause(expectedException);

    verify(connection).close();
  }

  @Test
  void tableExists_negative_sqlExceptionWhenGettingTables() throws SQLException {
    setupContextMocks();
    var timerTableCheckService = new TimerTableCheckService(dataSource, context);

    var expectedException = new SQLException("Failed to get tables");
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getTables(isNull(), eq(SCHEMA_NAME), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE)))
      .thenThrow(expectedException);

    assertThatThrownBy(timerTableCheckService::tableExists)
      .isInstanceOf(DataRetrievalFailureException.class)
      .hasMessageContaining("Failed to check if table " + TIMER_TABLE_NAME + " exists")
      .hasCause(expectedException);

    verify(connection).close();
  }

  @Test
  void tableExists_negative_sqlExceptionWhenCheckingResultSet() throws SQLException {
    setupContextMocks();
    var timerTableCheckService = new TimerTableCheckService(dataSource, context);

    var expectedException = new SQLException("Failed to check result set");
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getTables(isNull(), eq(SCHEMA_NAME), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE)))
      .thenReturn(resultSet);
    when(resultSet.next()).thenThrow(expectedException);

    assertThatThrownBy(timerTableCheckService::tableExists)
      .isInstanceOf(DataRetrievalFailureException.class)
      .hasMessageContaining("Failed to check if table " + TIMER_TABLE_NAME + " exists")
      .hasCause(expectedException);

    verify(connection).close();
    verify(resultSet).close();
  }

  @Test
  void tableExists_withUpperCase_tableIsPresent() throws SQLException {
    setupContextMocks();
    var timerTableCheckService = new TimerTableCheckService(dataSource, context, TableNameCase.UPPER);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getTables(isNull(), eq(SCHEMA_NAME), eq(TIMER_TABLE_NAME.toUpperCase()), eq(TABLE_TYPE)))
      .thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);

    boolean result = timerTableCheckService.tableExists();

    assertThat(result).isTrue();
    verify(connection).close();
    verify(resultSet).close();
  }

  @Test
  void tableExists_withMixedCase_tableIsPresent() throws SQLException {
    setupContextMocks();
    var timerTableCheckService = new TimerTableCheckService(dataSource, context, TableNameCase.MIXED);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getTables(isNull(), eq(SCHEMA_NAME), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE)))
      .thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);

    boolean result = timerTableCheckService.tableExists();

    assertThat(result).isTrue();
    verify(connection).close();
    verify(resultSet).close();
  }

  private void setupContextMocks() {
    when(context.getFolioModuleMetadata()).thenReturn(moduleMetadata);
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(moduleMetadata.getDBSchemaName(TENANT_ID)).thenReturn(SCHEMA_NAME);
  }
}
