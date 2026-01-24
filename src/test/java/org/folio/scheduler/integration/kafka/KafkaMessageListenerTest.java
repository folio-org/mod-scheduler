package org.folio.scheduler.integration.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.integration.kafka.model.EntitlementEventType.ENTITLE;
import static org.folio.scheduler.integration.kafka.model.EntitlementEventType.REVOKE;
import static org.folio.scheduler.integration.kafka.model.EntitlementEventType.UPGRADE;
import static org.folio.scheduler.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.scheduler.integration.kafka.model.ResourceEventType.DELETE;
import static org.folio.scheduler.integration.kafka.model.ResourceEventType.UPDATE;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerUnit;
import org.folio.scheduler.integration.kafka.model.EntitlementEvent;
import org.folio.scheduler.integration.kafka.model.EntitlementEventType;
import org.folio.scheduler.integration.kafka.model.ResourceEvent;
import org.folio.scheduler.integration.kafka.model.ResourceEventType;
import org.folio.scheduler.integration.kafka.model.ScheduledTimers;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaMessageListenerTest {

  private static final String TOPIC_NAME = "test.test.mgr-tenant-entitlement.scheduled-job";
  private static final String MODULE_ID1 = "mod-foo-1.0.0";
  private static final String MODULE_ID2 = "mod-foo-1.0.1";

  @InjectMocks
  private KafkaMessageListener kafkaMessageListener;

  @Mock
  private KafkaEventService eventService;

  @Mock
  private org.folio.spring.FolioModuleMetadata folioModuleMetadata;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(eventService);
  }

  private static ResourceEvent createResourceEvent(ResourceEventType type) {
    var scheduledTimers = new ScheduledTimers()
      .moduleId(MODULE_ID1)
      .applicationId("app-foo-1.0.0")
      .timers(List.of(routingEntry1()));

    var event = new ResourceEvent()
      .type(type)
      .resourceName("Scheduled Job")
      .tenant(TENANT_ID);

    return switch (type) {
      case CREATE -> event.newValue(scheduledTimers);
      case UPDATE -> event.oldValue(scheduledTimers).newValue(scheduledTimersAfterUpgrade());
      case DELETE -> event.oldValue(scheduledTimers);
      default -> event;
    };
  }

  private static EntitlementEvent createEntitlementEvent(EntitlementEventType type) {
    return new EntitlementEvent()
      .setModuleId(MODULE_ID1)
      .setType(type)
      .setTenantName(TENANT_ID);
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
      .unit(TimerUnit.MINUTE)
      .delay("1");
  }

  private static RoutingEntry routingEntry2() {
    return new RoutingEntry()
      .addMethodsItem("POST")
      .pathPattern("/test-entities/cleanup")
      .unit(TimerUnit.MINUTE)
      .delay("5");
  }

  @Nested
  class HandleScheduledJobEvent {

    @Test
    void positive_createEvent() {
      var event = createResourceEvent(CREATE);
      var consumerRecord = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);

      kafkaMessageListener.handleScheduledJobEvent(consumerRecord);

      verify(eventService).createTimers(event);
    }

    @Test
    void positive_updateEvent() {
      var event = createResourceEvent(UPDATE);
      var consumerRecord = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);

      kafkaMessageListener.handleScheduledJobEvent(consumerRecord);

      verify(eventService).updateTimers(event);
    }

    @Test
    void positive_deleteEvent() {
      var event = createResourceEvent(DELETE);
      var consumerRecord = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);

      kafkaMessageListener.handleScheduledJobEvent(consumerRecord);

      verify(eventService).deleteTimers(event);
    }

    @Test
    void negative_createEventThrowsException() {
      var event = createResourceEvent(CREATE);
      var consumerRecord = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);

      doThrow(new RuntimeException("Failed to create timers"))
        .when(eventService).createTimers(event);

      assertThatThrownBy(() -> kafkaMessageListener.handleScheduledJobEvent(consumerRecord))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to create timers");

      verify(eventService).createTimers(event);
    }

    @Test
    void negative_updateEventThrowsException() {
      var event = createResourceEvent(UPDATE);
      var consumerRecord = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);

      doThrow(new RuntimeException("Failed to update timers"))
        .when(eventService).updateTimers(event);

      assertThatThrownBy(() -> kafkaMessageListener.handleScheduledJobEvent(consumerRecord))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to update timers");

      verify(eventService).updateTimers(event);
    }

    @Test
    void negative_deleteEventThrowsException() {
      var event = createResourceEvent(DELETE);
      var consumerRecord = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);

      doThrow(new RuntimeException("Failed to delete timers"))
        .when(eventService).deleteTimers(event);

      assertThatThrownBy(() -> kafkaMessageListener.handleScheduledJobEvent(consumerRecord))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to delete timers");

      verify(eventService).deleteTimers(event);
    }
  }

  @Nested
  class HandleEntitlementEvent {

    @Test
    void positive_entitleEvent() {
      var event = createEntitlementEvent(ENTITLE);
      var consumerRecord = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);

      kafkaMessageListener.handleEntitlementEvent(consumerRecord);

      verify(eventService).enableTimers(event);
    }

    @Test
    void positive_upgradeEvent() {
      var event = createEntitlementEvent(UPGRADE);
      var consumerRecord = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);

      kafkaMessageListener.handleEntitlementEvent(consumerRecord);

      verify(eventService).enableTimers(event);
    }

    @Test
    void positive_revokeEvent() {
      var event = createEntitlementEvent(REVOKE);
      var consumerRecord = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);

      kafkaMessageListener.handleEntitlementEvent(consumerRecord);

      verify(eventService).disableTimers(event);
    }

    @Test
    void negative_entitleEventThrowsException() {
      var event = createEntitlementEvent(ENTITLE);
      var consumerRecord = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);

      doThrow(new RuntimeException("Failed to enable timers"))
        .when(eventService).enableTimers(event);

      assertThatThrownBy(() -> kafkaMessageListener.handleEntitlementEvent(consumerRecord))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to enable timers");

      verify(eventService).enableTimers(event);
    }

    @Test
    void negative_revokeEventThrowsException() {
      var event = createEntitlementEvent(REVOKE);
      var consumerRecord = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, event);

      doThrow(new RuntimeException("Failed to disable timers"))
        .when(eventService).disableTimers(event);

      assertThatThrownBy(() -> kafkaMessageListener.handleEntitlementEvent(consumerRecord))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to disable timers");

      verify(eventService).disableTimers(event);
    }
  }
}
