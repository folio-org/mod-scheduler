package org.folio.scheduler.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_UUID;
import static org.folio.scheduler.support.TestConstants.USER_ID_UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.quartz.JobKey.jobKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.integration.spring.SpringResourceAccessor;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.exception.MigrationException;
import org.folio.scheduler.mapper.TimerDescriptorMapper;
import org.folio.scheduler.repository.SchedulerTimerRepository;
import org.folio.scheduler.service.JobSchedulingService;
import org.folio.scheduler.support.TestValues;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.context.ApplicationContext;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RegroupTimerJobsMigrationTest {

  private static final String SELECT_ENABLED_TIMER_IDS =
    "SELECT id FROM timer WHERE timer_descriptor->'enabled' = 'true'";

  private final RegroupTimerJobsMigration unit = new RegroupTimerJobsMigration();

  @Mock private ApplicationContext mockAppContext;
  @Mock private TimerDescriptorMapper mapper;
  @Mock private SchedulerTimerRepository repository;
  @Mock private JobSchedulingService jobSchedulingService;
  @Mock private Scheduler scheduler;
  @Mock private FolioModuleMetadata moduleMetadata;
  @Mock private FolioExecutionContext folioExecutionContext;

  @BeforeEach
  void init() {
    unit.setFileOpener(new SpringResourceAccessor(mockAppContext));
  }

  @Test
  void execute_positive_noEnabledTimers() throws Exception {
    var resultSet = mock(ResultSet.class);
    when(resultSet.next()).thenReturn(false);

    unit.execute(dbMock(resultSet));

    verifyNoInteractions(jobSchedulingService, scheduler, repository, mapper);
  }

  @Test
  void execute_positive_regroupsSystemTimer() throws Exception {
    stubBeans();
    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    var entity = TestValues.timerDescriptorEntity(TestValues.timerDescriptor().type(TimerType.SYSTEM));
    var descriptor = TestValues.timerDescriptor().type(TimerType.SYSTEM);
    when(repository.findById(TIMER_UUID)).thenReturn(Optional.of(entity));
    when(mapper.toDescriptor(entity)).thenReturn(descriptor);

    unit.execute(dbMock(singleIdResultSet()));

    verify(scheduler).deleteJob(jobKey(TIMER_ID));
    verify(jobSchedulingService).schedule(descriptor);
  }

  @Test
  void execute_positive_regroupsUserTimerWithUserId() throws Exception {
    stubBeans();
    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    var entity = TestValues.timerDescriptorEntity(TestValues.timerDescriptor().type(TimerType.USER));
    var descriptor = TestValues.timerDescriptor().type(TimerType.USER).userId(USER_ID_UUID);
    when(repository.findById(TIMER_UUID)).thenReturn(Optional.of(entity));
    when(mapper.toDescriptor(entity)).thenReturn(descriptor);

    unit.execute(dbMock(singleIdResultSet()));

    verify(scheduler).deleteJob(jobKey(TIMER_ID));
    verify(jobSchedulingService).schedule(descriptor);
  }

  @Test
  void execute_positive_skipsUserTimerWithoutUserId() throws Exception {
    stubBeans();
    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    var entity = TestValues.timerDescriptorEntity(TestValues.timerDescriptor().type(TimerType.USER));
    when(repository.findById(TIMER_UUID)).thenReturn(Optional.of(entity));
    when(mapper.toDescriptor(entity)).thenReturn(TestValues.timerDescriptor().type(TimerType.USER));

    unit.execute(dbMock(singleIdResultSet()));

    verify(scheduler, never()).deleteJob(any());
    verifyNoInteractions(jobSchedulingService);
  }

  @Test
  void execute_positive_enabledTimerNotFoundInRepository() throws Exception {
    stubBeans();
    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    when(repository.findById(TIMER_UUID)).thenReturn(Optional.empty());

    unit.execute(dbMock(singleIdResultSet()));

    verify(repository).findById(TIMER_UUID);
    verifyNoInteractions(jobSchedulingService, scheduler);
  }

  @Test
  void execute_negative_blankTenantId() {
    stubBeans();
    when(folioExecutionContext.getTenantId()).thenReturn("  ");

    assertThatThrownBy(() -> unit.execute(dbMock(singleIdResultSet())))
      .isInstanceOf(MigrationException.class)
      .hasMessageContaining("tenant id is missing");

    verifyNoInteractions(jobSchedulingService, scheduler, repository);
  }

  @Test
  void execute_negative_deleteFails() throws Exception {
    stubBeans();
    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    var entity = TestValues.timerDescriptorEntity(TestValues.timerDescriptor().type(TimerType.SYSTEM));
    when(repository.findById(TIMER_UUID)).thenReturn(Optional.of(entity));
    when(mapper.toDescriptor(entity)).thenReturn(TestValues.timerDescriptor().type(TimerType.SYSTEM));
    when(scheduler.deleteJob(jobKey(TIMER_ID))).thenThrow(new SchedulerException("boom"));

    assertThatThrownBy(() -> unit.execute(dbMock(singleIdResultSet())))
      .isInstanceOf(MigrationException.class)
      .hasMessageContaining("Failed to delete existing scheduled job");

    verifyNoInteractions(jobSchedulingService);
  }

  private void stubBeans() {
    when(mockAppContext.getBean(TimerDescriptorMapper.class)).thenReturn(mapper);
    when(mockAppContext.getBean(SchedulerTimerRepository.class)).thenReturn(repository);
    when(mockAppContext.getBean(JobSchedulingService.class)).thenReturn(jobSchedulingService);
    when(mockAppContext.getBean(Scheduler.class)).thenReturn(scheduler);
    when(mockAppContext.getBean(FolioModuleMetadata.class)).thenReturn(moduleMetadata);
    when(mockAppContext.getBean(FolioExecutionContext.class)).thenReturn(folioExecutionContext);
  }

  private static ResultSet singleIdResultSet() throws Exception {
    var resultSet = mock(ResultSet.class);
    when(resultSet.next()).thenReturn(true).thenReturn(false);
    when(resultSet.getString("id")).thenReturn(TIMER_ID);
    return resultSet;
  }

  private static Database dbMock(ResultSet resultSet) throws Exception {
    var database = mock(Database.class);
    var liquibaseConnection = mock(JdbcConnection.class);
    var jdbcConnection = mock(Connection.class);
    var preparedStatement = mock(PreparedStatement.class);
    when(database.getConnection()).thenReturn(liquibaseConnection);
    when(liquibaseConnection.getWrappedConnection()).thenReturn(jdbcConnection);
    when(jdbcConnection.prepareStatement(SELECT_ENABLED_TIMER_IDS)).thenReturn(preparedStatement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    return database;
  }
}
