package org.folio.scheduler.integration.kafka.configuration;

import static lombok.AccessLevel.PRIVATE;

import java.util.concurrent.Executor;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.scheduler.integration.kafka.EventConfirmationSender;
import org.folio.scheduler.integration.kafka.KafkaEventConfirmationSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Log4j2
@NoArgsConstructor(access = PRIVATE)
public class EventConfirmationConfiguration {

  /**
   * Enables asynchronous confirmation sending along with the {@link #taskExecutor()} it runs on.
   *
   * <p>{@code @EnableAsync} is scoped here rather than to the application class because
   * {@link KafkaEventConfirmationSender} is the module's only {@code @Async} component.</p>
   */
  @EnableAsync
  @Configuration
  @ConditionalOnBooleanProperty(prefix = "application.event-confirmation", name = "enabled")
  public static class Enabled {

    @Bean
    public EventConfirmationSender eventConfirmationSender(EventConfirmationProperties properties,
      KafkaTemplate<String, ResourceResultEvent> kafkaTemplate) {
      log.info("Event confirmation is enabled.");

      return new KafkaEventConfirmationSender(properties.getTopic(), kafkaTemplate);
    }

    @Bean
    public Executor taskExecutor() {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(3);
      executor.setMaxPoolSize(10);
      executor.setQueueCapacity(50);
      executor.setThreadNamePrefix("AsyncEvt-");
      executor.initialize();
      return executor;
    }
  }

  @Configuration
  @ConditionalOnBooleanProperty(prefix = "application.event-confirmation", name = "enabled",
    havingValue = false, matchIfMissing = true)
  public static class Disabled {

    @Bean
    public EventConfirmationSender eventConfirmationSender() {
      log.info("Event confirmation is disabled.");
      return new EventConfirmationSender.NoOp();
    }
  }
}
