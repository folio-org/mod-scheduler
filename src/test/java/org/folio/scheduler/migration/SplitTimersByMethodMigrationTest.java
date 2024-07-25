package org.folio.scheduler.migration;

import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.integration.spring.SpringResourceAccessor;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.repository.SchedulerTimerRepository;
import org.folio.scheduler.service.JobSchedulingService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@UnitTest
@ExtendWith(MockitoExtension.class)
public class SplitTimersByMethodMigrationTest {

  SplitTimersByMethodMigration unit = new SplitTimersByMethodMigration();
  @Mock ApplicationContext mockAppContext;
  @Mock JobSchedulingService mockJobSchedulingService;
  @Mock SchedulerTimerRepository mockSchedulerTimerRepository;

  @BeforeEach
  void init() {
    unit.setFileOpener(new SpringResourceAccessor(mockAppContext));
    lenient().when(mockAppContext.getBean(JobSchedulingService.class)).thenReturn(mockJobSchedulingService);
    lenient().when(mockAppContext.getBean(SchedulerTimerRepository.class)).thenReturn(mockSchedulerTimerRepository);
    lenient().when(mockSchedulerTimerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void test_execute_positive() throws Exception {
    var routingEntry1 = new RoutingEntry();
    routingEntry1.methods(of("GET", "POST", "PUT"));
    var routingEntry2 = new RoutingEntry();
    routingEntry2.methods(of("PUT", "DELETE"));

    var mockResultSet = mock(ResultSet.class);
    when(mockResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);

    var uuid1 = UUID.fromString("7aa7c9e6-b6a7-4b93-92fc-858787d61b1a");
    var uuid2 = UUID.fromString("04ddd797-2275-4695-857c-29f1d82daf7d");

    when(mockResultSet.getString("id")).thenReturn(uuid1.toString()).thenReturn(uuid2.toString());
    when(mockSchedulerTimerRepository.findById(uuid1)).thenReturn(
      Optional.of(TimerDescriptorEntity.of(new TimerDescriptor(routingEntry1, true))));
    when(mockSchedulerTimerRepository.findById(uuid2)).thenReturn(
      Optional.of(TimerDescriptorEntity.of(new TimerDescriptor(routingEntry2, false))));

    var methodsPassed = new ArrayList<>();
    lenient().when(mockSchedulerTimerRepository.save(any())).thenAnswer(inv -> {
      TimerDescriptorEntity timer = inv.getArgument(0);
      methodsPassed.add(new ArrayList<>(timer.getTimerDescriptor().getRoutingEntry().getMethods()));
      return timer;
    });

    unit.execute(
      setupDbConnectionMock(Map.of(SplitTimersByMethodMigration.QUERY_TIMERS_WITH_MULTIPLE_METHODS, mockResultSet)));

    verify(mockSchedulerTimerRepository, times(5)).save(any());

    System.out.println(methodsPassed);

    // To ensure order of IDs matches the order used by Migration, that in its
    // turn uses Hash set - use Hash-based sorting by ID
    var idsOfTimersToSplit = new HashMap<String, RoutingEntry>();
    idsOfTimersToSplit.put(uuid1.toString(), routingEntry1);
    idsOfTimersToSplit.put(uuid2.toString(), routingEntry2);

    var i = new AtomicInteger(0);
    idsOfTimersToSplit.forEach((key, value) -> {
      for (int n = 1; n < value.getMethods().size(); n++) {
        assertThat(methodsPassed.get(i.getAndIncrement())).isEqualTo(List.of(value.getMethods().get(n)));
      }
    });

    verify(mockJobSchedulingService, times(2)).schedule(any());
  }

  protected Database setupDbConnectionMock(Map<String, ResultSet> mockQueryResponses) throws Exception {
    var mockLiquibaseDbAccess = mock(Database.class);
    var mockLiquibaseDbConnection = mock(JdbcConnection.class);
    var mockJdbcConnection = mock(Connection.class);
    when(mockLiquibaseDbAccess.getConnection()).thenReturn(mockLiquibaseDbConnection);
    when(mockLiquibaseDbConnection.getWrappedConnection()).thenReturn(mockJdbcConnection);

    for (Map.Entry<String, ResultSet> mockQueryResponse : mockQueryResponses.entrySet()) {
      var mockPrepStatement = mock(PreparedStatement.class);
      when(mockJdbcConnection.prepareStatement(mockQueryResponse.getKey())).thenReturn(mockPrepStatement);
      when(mockPrepStatement.executeQuery()).thenReturn(mockQueryResponse.getValue());
    }
    return mockLiquibaseDbAccess;
  }
}
