package org.folio.scheduler.integration.kafka;

import static java.util.Collections.singletonList;
import static org.folio.scheduler.utils.OkapiRequestUtils.getStaticPath;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.integration.kafka.model.ResourceEvent;
import org.folio.scheduler.integration.kafka.model.ScheduledTimers;
import org.folio.scheduler.integration.keycloak.SystemUserService;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaMessageListener {

  private final ObjectMapper objectMapper;
  private final SystemUserService systemUserService;
  private final FolioModuleMetadata folioModuleMetadata;
  private final SchedulerTimerService schedulerTimerService;

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
    var tenantId = resourceEvent.getTenant();

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeaders(tenantId))) {
      var scheduledTimers = objectMapper.convertValue(resourceEvent.getNewValue(), ScheduledTimers.class);
      for (var routingEntry : scheduledTimers.getTimers()) {
        var routingEntryKey = getRoutingEntryKey(routingEntry);
        log.info("Processing scheduled job event from kafka [routing entry: '{}']", routingEntryKey);
        var timerDescriptor = new TimerDescriptor().enabled(true).routingEntry(routingEntry);
        schedulerTimerService.create(timerDescriptor);
      }
    }
  }

  private Map<String, Collection<String>> prepareContextHeaders(String tenant) {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(TENANT, singletonList(tenant));
    headers.put(USER_ID, singletonList(systemUserService.findSystemUserId(tenant)));
    return headers;
  }

  private static String getRoutingEntryKey(RoutingEntry routingEntry) {
    var methods = String.join("|", routingEntry.getMethods());
    return methods + " " + getStaticPath(routingEntry);
  }
}
