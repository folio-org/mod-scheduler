package org.folio.scheduler.configuration;

import org.folio.scheduler.configuration.properties.RetryConfigurationProperties;
import org.folio.scheduler.service.jobs.TimerExecutionRetryClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

/**
 * Retry policy for timer HTTP calls.
 *
 * <p>Kept apart from {@link RetryConfiguration}, which unrelated test slices import for {@code @EnableRetry}.</p>
 */
@Configuration
public class TimerExecutionRetryConfiguration {

  /**
   * Builds a template whose {@code retryAttempts} include the initial call.
   *
   * @param properties - {@link RetryConfigurationProperties} component
   * @param classifier - decides which timer execution failures are retryable
   * @return {@link RetryTemplate} for {@link org.folio.scheduler.service.jobs.OkapiHttpRequestExecutor}
   */
  @Bean(name = "timerExecutionRetryTemplate")
  public RetryTemplate timerExecutionRetryTemplate(RetryConfigurationProperties properties,
                                                   TimerExecutionRetryClassifier classifier) {
    var config = properties.getConfig().get("timer-execution");
    return RetryTemplate.builder()
      .maxAttempts((int) config.getRetryAttempts())
      .retryOn(classifier::isRetryable)
      .exponentialBackoff(config.getRetryDelay(), config.getRetryMultiplier(), config.getMaxDelay())
      .build();
  }
}
