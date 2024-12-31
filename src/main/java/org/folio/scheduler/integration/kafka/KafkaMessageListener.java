package org.folio.scheduler.integration.kafka;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.scheduler.domain.model.TimerType.SYSTEM;
import static org.folio.scheduler.utils.OkapiRequestUtils.getStaticPath;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.common.utils.SemverUtils;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.integration.kafka.model.ResourceEvent;
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
    var operationType = resourceEvent.getType();

    switch (operationType) {
      case CREATE -> createTimers(resourceEvent);
      case UPDATE -> updateTimers(resourceEvent);
      case DELETE -> deleteTimers(resourceEvent);
      default -> log.warn("Unsupported operation type: consumerRecord = {}", consumerRecord);
    }
  }

  private void createTimers(ResourceEvent event) {
    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeaders(event.getTenant()))) {
      var moduleId = event.getNewValue().getModuleId();
      var moduleName = SemverUtils.getName(moduleId);
      var timers = event.getNewValue().getTimers();

      logCreatingTimers(timers);
      for (var routingEntry : timers) {
        schedulerTimerService.create(createTimerDescriptor(routingEntry, moduleName, moduleId));
      }
    }
  }

  private void updateTimers(ResourceEvent resourceEvent) {
    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata,
      prepareContextHeaders(resourceEvent.getTenant()))) {
      var moduleId = resourceEvent.getNewValue().getModuleId();
      var moduleName = SemverUtils.getName(moduleId);

      var timers = schedulerTimerService.findByModuleNameAndType(moduleName, SYSTEM);
      if (isNotEmpty(timers)) {
        logDeletingTimers(timers);
        for (var timer : timers) {
          schedulerTimerService.delete(timer.getId());
        }
      }

      var timerToCreate = resourceEvent.getNewValue();
      logCreatingTimers(timerToCreate.getTimers());
      for (var routingEntry : timerToCreate.getTimers()) {
        schedulerTimerService.create(createTimerDescriptor(routingEntry, moduleName, moduleId));
      }
    }
  }

  private void deleteTimers(ResourceEvent resourceEvent) {
    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata,
      prepareContextHeaders(resourceEvent.getTenant()))) {
      var moduleName = SemverUtils.getName(resourceEvent.getOldValue().getModuleId());
      var timers = schedulerTimerService.findByModuleNameAndType(moduleName, SYSTEM);
      if (isEmpty(timers)) {
        return;
      }

      logDeletingTimers(timers);
      for (var timer : timers) {
        schedulerTimerService.delete(timer.getId());
      }
    }
  }

  private Map<String, Collection<String>> prepareContextHeaders(String tenant) {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(TENANT, singletonList(tenant));
    headers.put(USER_ID, singletonList(systemUserService.findSystemUserId(tenant)));
    return headers;
  }

  private static TimerDescriptor createTimerDescriptor(RoutingEntry routingEntry, String moduleName, String moduleId) {
    return new TimerDescriptor().enabled(TRUE)
      .type(TimerType.SYSTEM)
      .moduleName(moduleName)
      .moduleId(moduleId)
      .routingEntry(routingEntry);
  }

  private static String getRoutingEntryKey(RoutingEntry routingEntry) {
    var methods = String.join("|", routingEntry.getMethods());
    return methods + " " + getStaticPath(routingEntry);
  }

  private static void logCreatingTimers(List<RoutingEntry> entries) {
    log.info("Processing scheduled job event from kafka: timers = {}",
      () -> mapItems(entries, KafkaMessageListener::getRoutingEntryKey));
  }

  private static void logDeletingTimers(List<TimerDescriptor> timers) {
    log.info("Deleting timers: timers = {}",
      () -> mapItems(timers, t -> getRoutingEntryKey(t.getRoutingEntry())));
  }
}
