package org.folio.scheduler.configuration.properties;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("application.timer.system")
public class SystemTimerConfigurationProperties {

  /**
   * Initial delay for SYSTEM simple timers.
   */
  private Duration initialDelay = Duration.ZERO;
}
