package org.folio.scheduler.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.folio.scheduler.integration.kafka.TimerTableCheckService.TableNameCase;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TimerTableCheckServiceTest {

  private static final String TIMER_TABLE_NAME = "timer";
  private static final String SCHEMA_NAME = TENANT_ID + "_mod_scheduler";

  @Mock
  private JdbcTemplate jdbcTemplate;
  @Mock
  private FolioExecutionContext context;
  @Mock
  private FolioModuleMetadata moduleMetadata;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(jdbcTemplate);
  }

  @Test
  void tableExists_positive_tableIsPresent() {
    setupContextMocks();
    var timerTableCheckService = new TimerTableCheckService(jdbcTemplate, context);

    when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(SCHEMA_NAME), eq(TIMER_TABLE_NAME)))
      .thenReturn(true);

    boolean result = timerTableCheckService.tableExists();

    assertThat(result).isTrue();
  }

  @Test
  void tableExists_positive_tableIsAbsent() {
    setupContextMocks();
    var timerTableCheckService = new TimerTableCheckService(jdbcTemplate, context);

    when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(SCHEMA_NAME), eq(TIMER_TABLE_NAME)))
      .thenReturn(false);

    boolean result = timerTableCheckService.tableExists();

    assertThat(result).isFalse();
  }

  @Test
  void tableExists_positive_tableIsPresentReturnsNull() {
    setupContextMocks();
    var timerTableCheckService = new TimerTableCheckService(jdbcTemplate, context);

    when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(SCHEMA_NAME), eq(TIMER_TABLE_NAME)))
      .thenReturn(null);

    boolean result = timerTableCheckService.tableExists();

    assertThat(result).isFalse();
  }

  @Test
  void tableExists_withUpperCase_tableIsPresent() {
    setupContextMocks();
    var timerTableCheckService = new TimerTableCheckService(jdbcTemplate, context, TableNameCase.UPPER);

    when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(SCHEMA_NAME),
      eq(TIMER_TABLE_NAME.toUpperCase())))
      .thenReturn(true);

    boolean result = timerTableCheckService.tableExists();

    assertThat(result).isTrue();
  }

  @Test
  void tableExists_withMixedCase_tableIsPresent() {
    setupContextMocks();
    var timerTableCheckService = new TimerTableCheckService(jdbcTemplate, context, TableNameCase.MIXED);

    when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(SCHEMA_NAME), eq(TIMER_TABLE_NAME)))
      .thenReturn(true);

    boolean result = timerTableCheckService.tableExists();

    assertThat(result).isTrue();
  }

  private void setupContextMocks() {
    when(context.getFolioModuleMetadata()).thenReturn(moduleMetadata);
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(moduleMetadata.getDBSchemaName(TENANT_ID)).thenReturn(SCHEMA_NAME);
  }
}
