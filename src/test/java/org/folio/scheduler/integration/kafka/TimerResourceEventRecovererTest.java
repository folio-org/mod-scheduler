package org.folio.scheduler.integration.kafka;

import static org.folio.integration.kafka.model.ResourceResultStatus.FAILURE;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.integration.kafka.model.ResourceEvent;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.scheduler.integration.kafka.model.ScheduledTimers;
import org.folio.scheduler.utils.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TimerResourceEventRecovererTest {

  private static final String EVENT_ID = "5f26fe20-d7bc-11ef-9cd2-0242ac120002";
  private static final String RESOURCE_NAME = "Scheduled Job";
  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String TOPIC = "folio.mgr-tenant-entitlements.scheduled-job";

  @Mock private ApplicationEventPublisher eventPublisher;
  @Spy private ObjectMapper objectMapper = TestUtils.OBJECT_MAPPER;
  @InjectMocks private TimerResourceEventRecoverer recoverer;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(eventPublisher);
  }

  @Test
  void accept_positive_resourceEvent_publishesFailureConfirmation() {
    var scheduledTimers = new ScheduledTimers().moduleId(MODULE_ID);
    var event = ResourceEvent.<ScheduledTimers>baseBuilder()
      .id(EVENT_ID)
      .tenant(TENANT_ID)
      .resourceName(RESOURCE_NAME)
      .newValue(scheduledTimers)
      .build();
    var record = new ConsumerRecord<String, Object>(TOPIC, 0, 0L, TENANT_ID, event);
    var exception = new RuntimeException("processing failed");

    recoverer.accept(record, exception);

    verify(eventPublisher).publishEvent((Object) argThat(e ->
      e instanceof ResourceResultEvent r
        && EVENT_ID.equals(r.getId())
        && TENANT_ID.equals(r.getTenant())
        && RESOURCE_NAME.equals(r.getResourceName())
        && MODULE_ID.equals(r.getModuleId())
        && r.getStatus() == FAILURE
        && r.getDetails() != null
    ));
  }

  @Test
  void accept_negative_nonResourceEventValue_doesNotPublish() {
    var record = new ConsumerRecord<String, Object>(TOPIC, 0, 0L, TENANT_ID, "plain-string-value");

    recoverer.accept(record, new RuntimeException("error"));

    // verifyNoMoreInteractions in tearDown asserts no publish happened
  }

  @Test
  void accept_positive_moduleIdFromOldValue_whenNewValueIsNull() {
    var oldTimers = new ScheduledTimers().moduleId(MODULE_ID);
    var event = ResourceEvent.<ScheduledTimers>baseBuilder()
      .id(EVENT_ID)
      .tenant(TENANT_ID)
      .oldValue(oldTimers)
      .build();
    var record = new ConsumerRecord<String, Object>(TOPIC, 0, 0L, TENANT_ID, event);

    recoverer.accept(record, new RuntimeException("error"));

    verify(eventPublisher).publishEvent((Object) argThat(e ->
      e instanceof ResourceResultEvent r && MODULE_ID.equals(r.getModuleId())
    ));
  }

  @Test
  void accept_positive_moduleIdIsNull_whenConvertValueFails() {
    doThrow(new IllegalArgumentException("conversion failed"))
      .when(objectMapper).convertValue(any(), any(Class.class));
    var event = ResourceEvent.<ScheduledTimers>baseBuilder()
      .id(EVENT_ID)
      .tenant(TENANT_ID)
      .newValue(new ScheduledTimers())
      .build();
    var record = new ConsumerRecord<String, Object>(TOPIC, 0, 0L, TENANT_ID, event);

    recoverer.accept(record, new RuntimeException("error"));

    verify(eventPublisher).publishEvent((Object) argThat(e ->
      e instanceof ResourceResultEvent r && r.getModuleId() == null
    ));
  }
}
