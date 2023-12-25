package org.folio.scheduler.integration.kafka;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.folio.scheduler.domain.dto.TimerUnit.MINUTE;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.utils.TestUtils.OBJECT_MAPPER;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.integration.kafka.model.ResourceEvent;
import org.folio.scheduler.integration.kafka.model.ResourceEventType;
import org.folio.scheduler.integration.keycloak.SystemUserService;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaMessageListenerTest {

  private static final String TOPIC_NAME = "test.test.mgr-tenant-entitlement.scheduled-job";
  private static final String SYSTEM_USER_ID = UUID.randomUUID().toString();

  @InjectMocks private KafkaMessageListener kafkaMessageListener;
  @Mock private SystemUserService systemUserService;
  @Mock private FolioModuleMetadata folioModuleMetadata;
  @Mock private SchedulerTimerService schedulerTimerService;
  @Spy private final ObjectMapper objectMapper = OBJECT_MAPPER;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(systemUserService, folioModuleMetadata, schedulerTimerService);
  }

  @Test
  void handleScheduledJobEvent_positive() {
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(folioModuleMetadata.getModuleName()).thenReturn("mod-scheduler");

    var record = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, resourceEvent());
    kafkaMessageListener.handleScheduledJobEvent(record);

    verify(objectMapper).convertValue(routingEntryAsMap(), RoutingEntry.class);
    verify(schedulerTimerService).create(new TimerDescriptor().enabled(true).routingEntry(routingEntry()));
  }

  @Test
  void handleScheduledJobEvent_negative() {
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(folioModuleMetadata.getModuleName()).thenReturn("mod-scheduler");
    var expectedDescriptor = new TimerDescriptor().enabled(true).routingEntry(routingEntry());
    when(schedulerTimerService.create(expectedDescriptor)).thenThrow(RuntimeException.class);

    var record = new ConsumerRecord<>(TOPIC_NAME, 0, 0, TENANT_ID, resourceEvent());
    assertThatThrownBy(() -> kafkaMessageListener.handleScheduledJobEvent(record))
      .isInstanceOf(RuntimeException.class);

    verify(objectMapper).convertValue(routingEntryAsMap(), RoutingEntry.class);
  }

  private static ResourceEvent resourceEvent() {
    return new ResourceEvent()
      .type(ResourceEventType.CREATE)
      .resourceName("Scheduled Job")
      .tenant(TENANT_ID)
      .newValue(routingEntryAsMap());
  }

  private static Map<String, Object> routingEntryAsMap() {
    return Map.of(
      "methods", singletonList("POST"),
      "pathPattern", "/test-entities/expire",
      "unit", "minute",
      "delay", "1");
  }

  private static RoutingEntry routingEntry() {
    return new RoutingEntry()
      .addMethodsItem("POST")
      .pathPattern("/test-entities/expire")
      .unit(MINUTE)
      .delay("1");
  }
}
