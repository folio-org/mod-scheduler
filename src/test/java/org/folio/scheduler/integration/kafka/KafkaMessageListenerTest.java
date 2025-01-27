package org.folio.scheduler.integration.kafka;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.folio.scheduler.domain.dto.TimerUnit.MINUTE;
import static org.folio.scheduler.domain.model.TimerType.SYSTEM;
import static org.folio.scheduler.domain.model.TimerType.USER;
import static org.folio.scheduler.integration.kafka.model.EntitlementEventType.ENTITLE;
import static org.folio.scheduler.integration.kafka.model.EntitlementEventType.REVOKE;
import static org.folio.scheduler.integration.kafka.model.EntitlementEventType.UPGRADE;
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
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.integration.kafka.model.EntitlementEvent;
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
  private static final String MODULE_ID1 = "mod-foo-1.0.0";
  private static final String MODULE_ID2 = "mod-foo-1.0.1";

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

    var consumerRecord = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, createResourceEvent());
    kafkaMessageListener.handleScheduledJobEvent(consumerRecord);

    verify(schedulerTimerService).create(
      new TimerDescriptor().enabled(true).type(TimerType.SYSTEM)
        .moduleName(MODULE_NAME).moduleId(MODULE_ID1).routingEntry(routingEntry1()));
  }

  @Test
  void handleScheduledJobEvent_positive_update() {
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(schedulerTimerService.findByModuleNameAndType(MODULE_NAME, SYSTEM)).thenReturn(
      List.of(new TimerDescriptor().id(TIMER_ID).type(TimerType.SYSTEM).enabled(true).routingEntry(routingEntry1())));

    var consumerRec = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, udpateResourceEvent());
    kafkaMessageListener.handleScheduledJobEvent(consumerRec);

    verify(schedulerTimerService).delete(TIMER_ID);
    verify(schedulerTimerService).findByModuleNameAndType(MODULE_NAME, SYSTEM);
    verify(schedulerTimerService).create(
      new TimerDescriptor().type(TimerType.SYSTEM).enabled(true)
        .moduleName(MODULE_NAME).moduleId(MODULE_ID2).routingEntry(routingEntry2()));
  }

  @Test
  void handleScheduledJobEvent_positive_delete() {
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(schedulerTimerService.findByModuleNameAndType(MODULE_NAME, SYSTEM)).thenReturn(
      List.of(new TimerDescriptor().id(TIMER_ID).enabled(true).routingEntry(routingEntry1())));

    var consumerRec = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, deleteResourceEvent());
    kafkaMessageListener.handleScheduledJobEvent(consumerRec);

    verify(schedulerTimerService).delete(TIMER_ID);
    verify(schedulerTimerService).findByModuleNameAndType(MODULE_NAME, SYSTEM);
  }

  @Test
  void handleScheduledJobEvent_negative() {
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    var expectedDescriptor = new TimerDescriptor().enabled(true).type(TimerType.SYSTEM)
      .moduleName(MODULE_NAME).moduleId(MODULE_ID1).routingEntry(routingEntry1());
    when(schedulerTimerService.create(expectedDescriptor)).thenThrow(RuntimeException.class);

    var consumerRec = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, createResourceEvent());
    assertThatThrownBy(() -> kafkaMessageListener.handleScheduledJobEvent(consumerRec))
      .isInstanceOf(RuntimeException.class);

    verify(schedulerTimerService).create(expectedDescriptor);
  }

  @Test
  void handleEntitlementEvent() {
    var event = entitlementEvent();
    var consumerRec = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);

    kafkaMessageListener.handleEntitlementEvent(consumerRec);

    verify(schedulerTimerService).switchModuleTimers("mod-foo", USER, true);
  }

  @Test
  void handleEntitlementEvent_upgrade() {
    var event = entitlementUpgradeEvent();
    var consumerRec = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);

    kafkaMessageListener.handleEntitlementEvent(consumerRec);

    verify(schedulerTimerService).switchModuleTimers("mod-foo", USER, true);
  }

  @Test
  void handleEntitlementEvent_revoke() {
    var event = entitlementRevokeEvent();
    var consumerRec = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);

    kafkaMessageListener.handleEntitlementEvent(consumerRec);

    verify(schedulerTimerService).switchModuleTimers("mod-foo", USER, false);
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

  private static EntitlementEvent entitlementEvent() {
    return new EntitlementEvent()
      .setModuleId("mod-foo-1.0.0")
      .setType(ENTITLE)
      .setTenantName(TENANT_ID);
  }

  private static EntitlementEvent entitlementUpgradeEvent() {
    return new EntitlementEvent()
      .setModuleId("mod-foo-1.0.0")
      .setType(UPGRADE)
      .setTenantName(TENANT_ID);
  }

  private static EntitlementEvent entitlementRevokeEvent() {
    return new EntitlementEvent()
      .setModuleId("mod-foo-1.0.0")
      .setType(REVOKE)
      .setTenantName(TENANT_ID);
  }

  private static ScheduledTimers scheduledTimersBeforeUpgrade() {
    return new ScheduledTimers()
      .moduleId(MODULE_ID1)
      .applicationId("app-foo-1.0.0")
      .timers(List.of(routingEntry1()));
  }

  private static ScheduledTimers scheduledTimersAfterUpgrade() {
    return new ScheduledTimers()
      .moduleId(MODULE_ID2)
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
