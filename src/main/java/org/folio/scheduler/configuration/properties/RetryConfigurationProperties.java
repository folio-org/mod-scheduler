package org.folio.scheduler.configuration.properties;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "application.retry")
public class RetryConfigurationProperties {

  /**
   * Retry configuration map.
   */
  private Map<String, RetryProperties> config = Collections.emptyMap();

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  public static class RetryProperties {

    /**
     * Retry delay.
     */
    private Duration retryDelay;

    /**
     * A number for retry attempts.
     */
    private Duration maxDelay;

    /**
     * A number for retry attempts.
     */
    private long retryAttempts;

    /**
     * A number for retry multiplier between attempts.
     */
    private double retryMultiplier;
  }
}
