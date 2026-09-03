package org.folio.scheduler.integration.kafka;

import static org.folio.integration.kafka.model.ResourceResultStatus.FAILURE;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.integration.kafka.model.ResourceEvent;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.scheduler.integration.kafka.model.ScheduledTimers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Log4j2
@Component
@RequiredArgsConstructor
public class TimerResourceEventRecoverer implements ConsumerRecordRecoverer {

  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  @Override
  public void accept(ConsumerRecord<?, ?> consumerRecord, Exception exception) {
    if (consumerRecord.value() instanceof ResourceEvent<?> resourceEvent) {
      eventPublisher.publishEvent(createResourceResultEvent(resourceEvent, exception));
    } else {
      log.error("Expected ResourceEvent but got: record = {}. Original exception = {}", consumerRecord, exception);
    }
  }

  private ResourceResultEvent createResourceResultEvent(ResourceEvent<?> resourceEvent, Exception exception) {
    return ResourceResultEvent.builder()
      .id(resourceEvent.getId())
      .tenant(resourceEvent.getTenant())
      .resourceName(resourceEvent.getResourceName())
      .moduleId(getModuleId(resourceEvent))
      .status(FAILURE)
      .details(ExceptionUtils.getMessage(exception))
      .build();
  }

  private String getModuleId(ResourceEvent<?> resourceEvent) {
    var value = resourceEvent.getNewValue() != null ? resourceEvent.getNewValue() : resourceEvent.getOldValue();
    try {
      var timers = objectMapper.convertValue(value, ScheduledTimers.class);
      return  timers.getModuleId();
    } catch (IllegalArgumentException e) {
      log.info("Failed to extract moduleId from resource event: record = {}, exception = {}. Returning null",
        resourceEvent, e.getMessage());
      return null;
    }
  }
}
