package org.folio.scheduler.integration.kafka.configuration;

import lombok.extern.log4j.Log4j2;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.scheduler.integration.kafka.EventConfirmationSender;
import org.folio.scheduler.integration.kafka.KafkaEventConfirmationSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Log4j2
@Configuration
public class EventConfirmationConfiguration {

  @Bean
  public EventConfirmationSender eventConfirmationSender(EventConfirmationProperties properties,
    KafkaTemplate<String, ResourceResultEvent> kafkaTemplate) {
    log.info("Event confirmation is {}.", properties.isEnabled() ? "enabled" : "disabled");

    return properties.isEnabled()
      ? new KafkaEventConfirmationSender(properties.getTopic(), kafkaTemplate)
      : new EventConfirmationSender.NoOp();
  }
}
