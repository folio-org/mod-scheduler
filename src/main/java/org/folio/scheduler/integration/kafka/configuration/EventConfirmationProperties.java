package org.folio.scheduler.integration.kafka.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("application.event-confirmation")
public class EventConfirmationProperties {

  /**
   * Flag to enable/disable sending confirmation events.
   */
  private boolean enabled;

  /**
   * Kafka topic for sending confirmation events.
   */
  private String topic;
}
