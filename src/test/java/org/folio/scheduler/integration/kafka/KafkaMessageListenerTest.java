package org.folio.scheduler.integration.kafka;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.folio.scheduler.domain.dto.TimerUnit.MINUTE;
import static org.folio.scheduler.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.scheduler.integration.kafka.model.ResourceEventType.DELETE;
import static org.folio.scheduler.integration.kafka.model.ResourceEventType.UPDATE;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.integration.kafka.model.ResourceEvent;
import org.folio.scheduler.integration.kafka.model.ScheduledTimers;
import org.folio.scheduler.integration.keycloak.SystemUserService;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaMessageListenerTest {

  private static final String TOPIC_NAME = "test.test.mgr-tenant-entitlement.scheduled-job";
  private static final String SYSTEM_USER_ID = UUID.randomUUID().toString();
  private static final UUID TIMER_ID = UUID.randomUUID();
  private static final String MODULE_NAME = "mod-foo";

  @InjectMocks private KafkaMessageListener kafkaMessageListener;
  @Mock private SystemUserService systemUserService;
  @Mock private FolioModuleMetadata folioModuleMetadata;
  @Mock private SchedulerTimerService schedulerTimerService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(systemUserService, schedulerTimerService);
  }

  @Test
  void handleScheduledJobEvent_positive_create() {
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);

    var record = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, createResourceEvent());
    kafkaMessageListener.handleScheduledJobEvent(record);

    verify(schedulerTimerService).create(
      new TimerDescriptor().enabled(true).moduleName(MODULE_NAME).routingEntry(routingEntry1()));
  }

  @Test
  void handleScheduledJobEvent_positive_update() {
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(schedulerTimerService.findByModuleName(MODULE_NAME)).thenReturn(
      List.of(new TimerDescriptor().id(TIMER_ID).enabled(true).routingEntry(routingEntry1())));

    var record = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, udpateResourceEvent());
    kafkaMessageListener.handleScheduledJobEvent(record);

    verify(schedulerTimerService).delete(TIMER_ID);
    verify(schedulerTimerService).findByModuleName(MODULE_NAME);
    verify(schedulerTimerService).create(
      new TimerDescriptor().enabled(true).moduleName(MODULE_NAME).routingEntry(routingEntry2()));
  }

  @Test
  void handleScheduledJobEvent_positive_delete() {
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(schedulerTimerService.findByModuleName(MODULE_NAME)).thenReturn(
      List.of(new TimerDescriptor().id(TIMER_ID).enabled(true).routingEntry(routingEntry1())));

    var record = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, deleteResourceEvent());
    kafkaMessageListener.handleScheduledJobEvent(record);

    verify(schedulerTimerService).delete(TIMER_ID);
    verify(schedulerTimerService).findByModuleName(MODULE_NAME);
  }

  @Test
  void handleScheduledJobEvent_negative() {
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    var expectedDescriptor = new TimerDescriptor().enabled(true).moduleName(MODULE_NAME).routingEntry(routingEntry1());
    when(schedulerTimerService.create(expectedDescriptor)).thenThrow(RuntimeException.class);

    var record = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, createResourceEvent());
    assertThatThrownBy(() -> kafkaMessageListener.handleScheduledJobEvent(record))
      .isInstanceOf(RuntimeException.class);

    verify(schedulerTimerService).create(expectedDescriptor);
  }

  private static ResourceEvent createResourceEvent() {
    return new ResourceEvent()
      .type(CREATE)
      .resourceName("Scheduled Job")
      .tenant(TENANT_ID)
      .newValue(scheduledTimersBeforeUpgrade());
  }

  private static ResourceEvent udpateResourceEvent() {
    return new ResourceEvent()
      .type(UPDATE)
      .resourceName("Scheduled Job")
      .tenant(TENANT_ID)
      .oldValue(scheduledTimersBeforeUpgrade())
      .newValue(scheduledTimersAfterUpgrade());
  }

  private static ResourceEvent deleteResourceEvent() {
    return new ResourceEvent()
      .type(DELETE)
      .resourceName("Scheduled Job")
      .tenant(TENANT_ID)
      .oldValue(scheduledTimersBeforeUpgrade());
  }

  private static ScheduledTimers scheduledTimersBeforeUpgrade() {
    return new ScheduledTimers()
      .moduleId("mod-foo-1.0.0")
      .applicationId("app-foo-1.0.0")
      .timers(List.of(routingEntry1()));
  }

  private static ScheduledTimers scheduledTimersAfterUpgrade() {
    return new ScheduledTimers()
      .moduleId("mod-foo-1.0.1")
      .applicationId("app-foo-1.0.1")
      .timers(List.of(routingEntry2()));
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
      .pathPattern("/test-entities/expire")
      .unit(MINUTE)
      .delay("1");
  }
}
