package org.folio.scheduler.integration.kafka;

import static org.folio.integration.kafka.model.ResourceResultStatus.FAILURE;
import static org.folio.integration.kafka.model.ResourceResultStatus.SUCCESS;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaEventConfirmationSenderTest {

  private static final String TOPIC_BASE = "mgr-tenant-entitlements.resource-result";
  private static final String TOPIC = "folio." + TOPIC_BASE;
  private static final String EVENT_ID = "5f26fe20-d7bc-11ef-9cd2-0242ac120002";
  private static final String MODULE_ID = "mod-foo-1.0.0";

  @Mock private KafkaTemplate<String, ResourceResultEvent> kafkaTemplate;

  private KafkaEventConfirmationSender sender;

  @BeforeEach
  void setUp() {
    sender = new KafkaEventConfirmationSender(TOPIC_BASE, kafkaTemplate);
  }

  @Test
  void onSuccessfulResourceResult_positive_sendsToCorrectTopic() {
    var event = ResourceResultEvent.builder()
      .id(EVENT_ID).tenant(TENANT_ID).moduleId(MODULE_ID).status(SUCCESS).build();
    when(kafkaTemplate.send(TOPIC, TENANT_ID, event))
      .thenReturn(CompletableFuture.completedFuture(null));

    sender.onSuccessfulResourceResult(event);

    verify(kafkaTemplate).send(TOPIC, TENANT_ID, event);
  }

  @Test
  void onSuccessfulResourceResult_negative_sendFails_noExceptionPropagated() {
    var event = ResourceResultEvent.builder()
      .id(EVENT_ID).tenant(TENANT_ID).moduleId(MODULE_ID).status(SUCCESS).build();
    when(kafkaTemplate.send(TOPIC, TENANT_ID, event))
      .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

    sender.onSuccessfulResourceResult(event);

    verify(kafkaTemplate).send(TOPIC, TENANT_ID, event);
  }

  @Test
  void onFailedResourceResult_positive_sendsToCorrectTopic() {
    var event = ResourceResultEvent.builder()
      .id(EVENT_ID).tenant(TENANT_ID).moduleId(MODULE_ID).status(FAILURE).build();
    when(kafkaTemplate.send(TOPIC, TENANT_ID, event))
      .thenReturn(CompletableFuture.completedFuture(null));

    sender.onFailedResourceResult(event);

    verify(kafkaTemplate).send(TOPIC, TENANT_ID, event);
  }
}
