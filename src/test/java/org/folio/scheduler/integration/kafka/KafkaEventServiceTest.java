package org.folio.scheduler.integration.kafka;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.domain.dto.TimerUnit.MINUTE;
import static org.folio.scheduler.domain.model.TimerType.SYSTEM;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.integration.kafka.model.EntitlementEvent;
import org.folio.scheduler.integration.kafka.model.ResourceEvent;
import org.folio.scheduler.integration.kafka.model.ScheduledTimers;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaEventServiceTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String MODULE_NAME = "mod-foo";
  private static final String APPLICATION_ID = "app-foo-1.0.0";

  @Mock private SchedulerTimerService schedulerTimerService;
  @Mock private FolioModuleMetadata folioModuleMetadata;
  @InjectMocks
  private KafkaEventService kafkaEventService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(schedulerTimerService);
  }

  private static ResourceEvent createResourceEvent(List<RoutingEntry> routingEntries) {
    var scheduledTimers = new ScheduledTimers()
      .moduleId(MODULE_ID)
      .applicationId(APPLICATION_ID)
      .timers(routingEntries);

    return new ResourceEvent()
      .tenant(TENANT_ID)
      .newValue(scheduledTimers);
  }

  private static RoutingEntry routingEntry1() {
    return new RoutingEntry()
      .addMethodsItem("POST")
      .pathPattern("/test-entities/expire")
      .unit(MINUTE)
      .delay("1");
  }

  private static RoutingEntry routingEntry2() {
    return new RoutingEntry()
      .addMethodsItem("POST")
      .pathPattern("/test-entities/cleanup")
      .unit(MINUTE)
      .delay("5");
  }

  @Nested
  class CreateTimers {

    @Test
    void positive_singleRoutingEntry() {
      var event = createResourceEvent(List.of(routingEntry1()));

      kafkaEventService.createTimers(event);

      verify(schedulerTimerService).create(argThat(descriptor ->
        descriptor.getEnabled().equals(TRUE)
          && descriptor.getType() == TimerType.SYSTEM
          && Objects.equals(descriptor.getModuleName(), MODULE_NAME)
          && Objects.equals(descriptor.getModuleId(), MODULE_ID)
          && descriptor.getRoutingEntry().equals(routingEntry1())
      ));
    }

    @Test
    void positive_multipleRoutingEntries() {
      var routingEntry1 = routingEntry1();
      var routingEntry2 = routingEntry2();
      var event = createResourceEvent(List.of(routingEntry1, routingEntry2));

      kafkaEventService.createTimers(event);

      verify(schedulerTimerService, times(2)).create(any(TimerDescriptor.class));
      verify(schedulerTimerService).create(argThat(descriptor ->
        descriptor.getRoutingEntry().equals(routingEntry1)
      ));
      verify(schedulerTimerService).create(argThat(descriptor ->
        descriptor.getRoutingEntry().equals(routingEntry2)
      ));
    }

    @Test
    void positive_emptyRoutingEntries() {
      var event = createResourceEvent(emptyList());

      kafkaEventService.createTimers(event);

      verify(schedulerTimerService, never()).create(any(TimerDescriptor.class));
    }

    @Test
    void positive_verifyTimerDescriptorProperties() {
      var routingEntry = routingEntry1();
      var event = createResourceEvent(List.of(routingEntry));

      kafkaEventService.createTimers(event);

      verify(schedulerTimerService).create(argThat(descriptor ->
        descriptor.getEnabled().equals(TRUE)
        && descriptor.getType() == TimerType.SYSTEM
        && Objects.equals(descriptor.getModuleName(), MODULE_NAME)
        && Objects.equals(descriptor.getModuleId(), MODULE_ID)
        && Objects.equals(descriptor.getRoutingEntry().getPathPattern(), "/test-entities/expire")
        && descriptor.getRoutingEntry().getMethods().contains("POST")
        && descriptor.getRoutingEntry().getUnit() == MINUTE
        && Objects.equals(descriptor.getRoutingEntry().getDelay(), "1")));
    }

    @Test
    void negative_schedulerTimerServiceThrowsException() {
      var event = createResourceEvent(List.of(routingEntry1()));
      var expectedException = new RuntimeException("Failed to create timer");

      when(schedulerTimerService.create(any(TimerDescriptor.class)))
        .thenThrow(expectedException);

      assertThatThrownBy(() -> kafkaEventService.createTimers(event))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to create timer");

      verify(schedulerTimerService).create(any(TimerDescriptor.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullParameterProvider")
    void negative_nullParameters(String testName, ResourceEvent event, Class<? extends Exception> expectedException) {
      assertThatThrownBy(() -> kafkaEventService.createTimers(event))
        .isInstanceOf(expectedException);

      verify(schedulerTimerService, never()).create(any(TimerDescriptor.class));
    }

    private static Stream<Arguments> nullParameterProvider() {
      return Stream.of(
        Arguments.of("negative_nullNewValue",
          new ResourceEvent()
            .tenant(TENANT_ID)
            .newValue(null),
          NullPointerException.class),
        Arguments.of("negative_nullTimersList",
          new ResourceEvent()
            .tenant(TENANT_ID)
            .newValue(new ScheduledTimers()
              .moduleId(MODULE_ID)
              .applicationId(APPLICATION_ID)
              .timers(null)),
          NullPointerException.class),
        Arguments.of("negative_nullModuleId",
          new ResourceEvent()
            .tenant(TENANT_ID)
            .newValue(new ScheduledTimers()
              .moduleId(null)
              .applicationId(APPLICATION_ID)
              .timers(List.of(routingEntry1()))),
          IllegalArgumentException.class)
      );
    }

    @Test
    void negative_partialFailure() {
      var routingEntry1 = routingEntry1();
      var routingEntry2 = routingEntry2();
      var event = createResourceEvent(List.of(routingEntry1, routingEntry2));

      when(schedulerTimerService.create(argThat(d ->
        d != null && d.getRoutingEntry() != null && d.getRoutingEntry().equals(routingEntry1))))
        .thenReturn(new TimerDescriptor());
      when(schedulerTimerService.create(argThat(d ->
        d != null && d.getRoutingEntry() != null && d.getRoutingEntry().equals(routingEntry2))))
        .thenThrow(new RuntimeException("Failed to create second timer"));

      assertThatThrownBy(() -> kafkaEventService.createTimers(event))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to create second timer");

      verify(schedulerTimerService, times(2)).create(any(TimerDescriptor.class));
    }
  }

  @Nested
  class UpdateTimers {

    @Test
    void positive_withExistingTimers() {
      var oldRoutingEntry = routingEntry1();
      var newRoutingEntry = routingEntry2();
      var oldTimers = new ScheduledTimers()
        .moduleId(MODULE_ID)
        .applicationId(APPLICATION_ID)
        .timers(List.of(oldRoutingEntry));
      var newTimers = new ScheduledTimers()
        .moduleId(MODULE_ID)
        .applicationId(APPLICATION_ID)
        .timers(List.of(newRoutingEntry));
      var event = new ResourceEvent()
        .tenant(TENANT_ID)
        .oldValue(oldTimers)
        .newValue(newTimers);

      var existingTimer = new TimerDescriptor()
        .id(UUID.randomUUID())
        .enabled(TRUE)
        .type(TimerType.SYSTEM)
        .moduleName(MODULE_NAME)
        .moduleId(MODULE_ID)
        .routingEntry(oldRoutingEntry);

      when(schedulerTimerService.findByModuleNameAndType(MODULE_NAME, SYSTEM))
        .thenReturn(List.of(existingTimer));

      kafkaEventService.updateTimers(event);

      verify(schedulerTimerService).findByModuleNameAndType(MODULE_NAME, SYSTEM);
      verify(schedulerTimerService).delete(existingTimer.getId());
      verify(schedulerTimerService).create(argThat(descriptor ->
        descriptor.getEnabled().equals(TRUE)
          && descriptor.getType() == TimerType.SYSTEM
          && Objects.equals(descriptor.getModuleName(), MODULE_NAME)
          && Objects.equals(descriptor.getModuleId(), MODULE_ID)
          && descriptor.getRoutingEntry().equals(newRoutingEntry)
      ));
    }

    @Test
    void positive_withoutExistingTimers() {
      var newRoutingEntry = routingEntry2();
      var newTimers = new ScheduledTimers()
        .moduleId(MODULE_ID)
        .applicationId(APPLICATION_ID)
        .timers(List.of(newRoutingEntry));
      var event = new ResourceEvent()
        .tenant(TENANT_ID)
        .newValue(newTimers);

      when(schedulerTimerService.findByModuleNameAndType(MODULE_NAME, SYSTEM))
        .thenReturn(emptyList());

      kafkaEventService.updateTimers(event);

      verify(schedulerTimerService).findByModuleNameAndType(MODULE_NAME, SYSTEM);
      verify(schedulerTimerService, never()).delete(any(UUID.class));
      verify(schedulerTimerService).create(argThat(descriptor ->
        descriptor.getRoutingEntry().equals(newRoutingEntry)
      ));
    }

    @Test
    void positive_multipleExistingTimers() {
      var oldRoutingEntry1 = routingEntry1();
      var oldRoutingEntry2 = routingEntry2();
      var newRoutingEntry = routingEntry1();
      var newTimers = new ScheduledTimers()
        .moduleId(MODULE_ID)
        .applicationId(APPLICATION_ID)
        .timers(List.of(newRoutingEntry));
      var event = new ResourceEvent()
        .tenant(TENANT_ID)
        .newValue(newTimers);

      var existingTimer1 = new TimerDescriptor()
        .id(UUID.randomUUID())
        .routingEntry(oldRoutingEntry1);
      var existingTimer2 = new TimerDescriptor()
        .id(UUID.randomUUID())
        .routingEntry(oldRoutingEntry2);

      when(schedulerTimerService.findByModuleNameAndType(MODULE_NAME, SYSTEM))
        .thenReturn(List.of(existingTimer1, existingTimer2));

      kafkaEventService.updateTimers(event);

      verify(schedulerTimerService).findByModuleNameAndType(MODULE_NAME, SYSTEM);
      verify(schedulerTimerService).delete(existingTimer1.getId());
      verify(schedulerTimerService).delete(existingTimer2.getId());
      verify(schedulerTimerService).create(any(TimerDescriptor.class));
    }

    @Test
    void positive_emptyNewTimers() {
      var oldRoutingEntry = routingEntry1();
      var newTimers = new ScheduledTimers()
        .moduleId(MODULE_ID)
        .applicationId(APPLICATION_ID)
        .timers(emptyList());
      var event = new ResourceEvent()
        .tenant(TENANT_ID)
        .newValue(newTimers);

      var existingTimer = new TimerDescriptor()
        .id(UUID.randomUUID())
        .routingEntry(oldRoutingEntry);

      when(schedulerTimerService.findByModuleNameAndType(MODULE_NAME, SYSTEM))
        .thenReturn(List.of(existingTimer));

      kafkaEventService.updateTimers(event);

      verify(schedulerTimerService).findByModuleNameAndType(MODULE_NAME, SYSTEM);
      verify(schedulerTimerService).delete(existingTimer.getId());
      verify(schedulerTimerService, never()).create(any(TimerDescriptor.class));
    }

    @Test
    void negative_deleteThrowsException() {
      var oldRoutingEntry = routingEntry1();
      var newRoutingEntry = routingEntry2();
      var newTimers = new ScheduledTimers()
        .moduleId(MODULE_ID)
        .applicationId(APPLICATION_ID)
        .timers(List.of(newRoutingEntry));
      var event = new ResourceEvent()
        .tenant(TENANT_ID)
        .newValue(newTimers);

      var existingTimer = new TimerDescriptor()
        .id(UUID.randomUUID())
        .routingEntry(oldRoutingEntry);

      when(schedulerTimerService.findByModuleNameAndType(MODULE_NAME, SYSTEM))
        .thenReturn(List.of(existingTimer));
      doThrow(new RuntimeException("Failed to delete timer"))
        .when(schedulerTimerService).delete(existingTimer.getId());

      assertThatThrownBy(() -> kafkaEventService.updateTimers(event))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to delete timer");

      verify(schedulerTimerService).findByModuleNameAndType(MODULE_NAME, SYSTEM);
      verify(schedulerTimerService).delete(existingTimer.getId());
    }

    @Test
    void negative_nullNewValue() {
      var event = new ResourceEvent()
        .tenant(TENANT_ID)
        .newValue(null);

      assertThatThrownBy(() -> kafkaEventService.updateTimers(event))
        .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class DeleteTimers {

    @Test
    void positive_withExistingTimers() {
      var oldTimers = new ScheduledTimers()
        .moduleId(MODULE_ID)
        .applicationId(APPLICATION_ID)
        .timers(List.of(routingEntry1()));
      var event = new ResourceEvent()
        .tenant(TENANT_ID)
        .oldValue(oldTimers);

      var existingTimer1 = new TimerDescriptor()
        .id(UUID.randomUUID())
        .routingEntry(routingEntry1());
      var existingTimer2 = new TimerDescriptor()
        .id(UUID.randomUUID())
        .routingEntry(routingEntry2());

      when(schedulerTimerService.findByModuleNameAndType(MODULE_NAME, SYSTEM))
        .thenReturn(List.of(existingTimer1, existingTimer2));

      kafkaEventService.deleteTimers(event);

      verify(schedulerTimerService).findByModuleNameAndType(MODULE_NAME, SYSTEM);
      verify(schedulerTimerService).delete(existingTimer1.getId());
      verify(schedulerTimerService).delete(existingTimer2.getId());
    }

    @Test
    void positive_withSingleTimer() {
      var oldTimers = new ScheduledTimers()
        .moduleId(MODULE_ID)
        .applicationId(APPLICATION_ID)
        .timers(List.of(routingEntry1()));
      var event = new ResourceEvent()
        .tenant(TENANT_ID)
        .oldValue(oldTimers);

      var existingTimer = new TimerDescriptor()
        .id(UUID.randomUUID())
        .routingEntry(routingEntry1());

      when(schedulerTimerService.findByModuleNameAndType(MODULE_NAME, SYSTEM))
        .thenReturn(List.of(existingTimer));

      kafkaEventService.deleteTimers(event);

      verify(schedulerTimerService).findByModuleNameAndType(MODULE_NAME, SYSTEM);
      verify(schedulerTimerService).delete(existingTimer.getId());
    }

    @Test
    void positive_withoutExistingTimers() {
      var oldTimers = new ScheduledTimers()
        .moduleId(MODULE_ID)
        .applicationId(APPLICATION_ID)
        .timers(List.of(routingEntry1()));
      var event = new ResourceEvent()
        .tenant(TENANT_ID)
        .oldValue(oldTimers);

      when(schedulerTimerService.findByModuleNameAndType(MODULE_NAME, SYSTEM))
        .thenReturn(emptyList());

      kafkaEventService.deleteTimers(event);

      verify(schedulerTimerService).findByModuleNameAndType(MODULE_NAME, SYSTEM);
      verify(schedulerTimerService, never()).delete(any(UUID.class));
    }

    @Test
    void negative_findByModuleNameThrowsException() {
      var oldTimers = new ScheduledTimers()
        .moduleId(MODULE_ID)
        .applicationId(APPLICATION_ID)
        .timers(List.of(routingEntry1()));
      var event = new ResourceEvent()
        .tenant(TENANT_ID)
        .oldValue(oldTimers);

      when(schedulerTimerService.findByModuleNameAndType(MODULE_NAME, SYSTEM))
        .thenThrow(new RuntimeException("Failed to find timers"));

      assertThatThrownBy(() -> kafkaEventService.deleteTimers(event))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to find timers");

      verify(schedulerTimerService).findByModuleNameAndType(MODULE_NAME, SYSTEM);
    }

    @Test
    void negative_deleteThrowsException() {
      var oldTimers = new ScheduledTimers()
        .moduleId(MODULE_ID)
        .applicationId(APPLICATION_ID)
        .timers(List.of(routingEntry1()));
      var event = new ResourceEvent()
        .tenant(TENANT_ID)
        .oldValue(oldTimers);

      var existingTimer = new TimerDescriptor()
        .id(UUID.randomUUID())
        .routingEntry(routingEntry1());

      when(schedulerTimerService.findByModuleNameAndType(MODULE_NAME, SYSTEM))
        .thenReturn(List.of(existingTimer));
      doThrow(new RuntimeException("Failed to delete timer"))
        .when(schedulerTimerService).delete(existingTimer.getId());

      assertThatThrownBy(() -> kafkaEventService.deleteTimers(event))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to delete timer");

      verify(schedulerTimerService).findByModuleNameAndType(MODULE_NAME, SYSTEM);
      verify(schedulerTimerService).delete(existingTimer.getId());
    }

    @Test
    void negative_nullOldValue() {
      var event = new ResourceEvent()
        .tenant(TENANT_ID)
        .oldValue(null);

      assertThatThrownBy(() -> kafkaEventService.deleteTimers(event))
        .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class EnableTimers {

    @Test
    void positive_enablesTimers() {
      var event = new EntitlementEvent()
        .setModuleId(MODULE_ID)
        .setTenantName(TENANT_ID);

      when(schedulerTimerService.switchModuleTimers(MODULE_NAME, true))
        .thenReturn(3);

      kafkaEventService.enableTimers(event);

      verify(schedulerTimerService).switchModuleTimers(MODULE_NAME, true);
    }

    @Test
    void positive_noTimersToEnable() {
      var event = new EntitlementEvent()
        .setModuleId(MODULE_ID)
        .setTenantName(TENANT_ID);

      when(schedulerTimerService.switchModuleTimers(MODULE_NAME, true))
        .thenReturn(0);

      kafkaEventService.enableTimers(event);

      verify(schedulerTimerService).switchModuleTimers(MODULE_NAME, true);
    }

    @Test
    void negative_switchTimersThrowsException() {
      var event = new EntitlementEvent()
        .setModuleId(MODULE_ID)
        .setTenantName(TENANT_ID);

      when(schedulerTimerService.switchModuleTimers(MODULE_NAME, true))
        .thenThrow(new RuntimeException("Failed to switch timers"));

      assertThatThrownBy(() -> kafkaEventService.enableTimers(event))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to switch timers");

      verify(schedulerTimerService).switchModuleTimers(MODULE_NAME, true);
    }

    @Test
    void negative_nullModuleId() {
      var event = new EntitlementEvent()
        .setModuleId(null)
        .setTenantName(TENANT_ID);

      assertThatThrownBy(() -> kafkaEventService.enableTimers(event))
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class DisableTimers {

    @Test
    void positive_disablesTimers() {
      var event = new EntitlementEvent()
        .setModuleId(MODULE_ID)
        .setTenantName(TENANT_ID);

      when(schedulerTimerService.switchModuleTimers(MODULE_NAME, false))
        .thenReturn(2);

      kafkaEventService.disableTimers(event);

      verify(schedulerTimerService).switchModuleTimers(MODULE_NAME, false);
    }

    @Test
    void positive_noTimersToDisable() {
      var event = new EntitlementEvent()
        .setModuleId(MODULE_ID)
        .setTenantName(TENANT_ID);

      when(schedulerTimerService.switchModuleTimers(MODULE_NAME, false))
        .thenReturn(0);

      kafkaEventService.disableTimers(event);

      verify(schedulerTimerService).switchModuleTimers(MODULE_NAME, false);
    }

    @Test
    void negative_switchTimersThrowsException() {
      var event = new EntitlementEvent()
        .setModuleId(MODULE_ID)
        .setTenantName(TENANT_ID);

      when(schedulerTimerService.switchModuleTimers(MODULE_NAME, false))
        .thenThrow(new RuntimeException("Failed to switch timers"));

      assertThatThrownBy(() -> kafkaEventService.disableTimers(event))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to switch timers");

      verify(schedulerTimerService).switchModuleTimers(MODULE_NAME, false);
    }

    @Test
    void negative_nullModuleId() {
      var event = new EntitlementEvent()
        .setModuleId(null)
        .setTenantName(TENANT_ID);

      assertThatThrownBy(() -> kafkaEventService.disableTimers(event))
        .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
