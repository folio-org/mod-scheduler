package org.folio.scheduler.integration.kafka;

import static java.util.Collections.singletonList;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.scheduler.integration.kafka.model.EntitlementEvent;
import org.folio.scheduler.integration.kafka.model.ResourceEvent;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaMessageListener {

  private final FolioModuleMetadata folioModuleMetadata;
  private final KafkaEventService eventService;

  /**
   * Handles scheduled job events.
   *
   * @param consumerRecord - a consumer records from Apache Kafka to process.
   */
  @KafkaListener(
    id = KafkaConstants.SCHEDULED_JOB_LISTENER_ID,
    containerFactory = "kafkaListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['scheduled-jobs'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['scheduled-jobs'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['scheduled-jobs'].concurrency}")
  public void handleScheduledJobEvent(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    var resourceEvent = consumerRecord.value();
    var tenant = resourceEvent.getTenant();

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeaders(tenant))) {
      var operationType = resourceEvent.getType();

      log.info("Received job {} event for {} in {}. Thread: {}", operationType, resourceEvent.getResourceName(),
        tenant, Thread.currentThread().getName());

      switch (operationType) {
        case CREATE -> eventService.createTimers(resourceEvent);
        case UPDATE -> eventService.updateTimers(resourceEvent);
        case DELETE -> eventService.deleteTimers(resourceEvent);
        default -> logUnsupportedOperationType(consumerRecord);
      }
    }
  }

  /**
   * Handles entitlement events.
   *
   * @param consumerRecord - a consumer records from Apache Kafka to process.
   */
  @KafkaListener(
    id = KafkaConstants.ENTITLEMENT_EVENTS_LISTENER_ID,
    containerFactory = "listenerContainerFactoryEntitlementEvent",
    topicPattern = "#{folioKafkaProperties.listener['entitlement-events'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['entitlement-events'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['entitlement-events'].concurrency}")
  public void handleEntitlementEvent(ConsumerRecord<String, EntitlementEvent> consumerRecord) {
    var event = consumerRecord.value();
    var tenant = event.getTenantName();

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeaders(tenant))) {
      var operationType = event.getType();

      log.info("Received entitlement {} event for {} in {}. Thread: {}", operationType, event.getModuleId(),
        tenant, Thread.currentThread().getName());

      switch (operationType) {
        case ENTITLE, UPGRADE -> eventService.enableTimers(event);
        case REVOKE -> eventService.disableTimers(event);
        default -> logUnsupportedOperationType(consumerRecord);
      }
    }
  }

  private static Map<String, Collection<String>> prepareContextHeaders(String tenant) {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(TENANT, singletonList(tenant));
    return headers;
  }

  private static void logUnsupportedOperationType(ConsumerRecord<?, ?> consumerRecord) {
    log.warn("Unsupported operation type: consumerRecord = {}", consumerRecord);
  }
}
