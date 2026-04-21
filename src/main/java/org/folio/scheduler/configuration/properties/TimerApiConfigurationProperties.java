package org.folio.scheduler.configuration.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("application.timer.api")
public class TimerApiConfigurationProperties {

  /**
   * Allows SYSTEM timers to be created, updated, or deleted via the public REST API.
   */
  private boolean allowSystemTimerMutation = false;
}
