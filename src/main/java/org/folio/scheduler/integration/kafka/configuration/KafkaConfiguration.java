package org.folio.scheduler.integration.kafka.configuration;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.scheduler.configuration.properties.RetryConfigurationProperties;
import org.folio.scheduler.integration.kafka.TimerTableCheckService;
import org.folio.scheduler.integration.kafka.model.EntitlementEvent;
import org.folio.scheduler.integration.kafka.model.ResourceEvent;
import org.folio.spring.FolioExecutionContext;
import org.hibernate.exception.SQLGrammarException;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.KafkaException.Level;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

@Log4j2
@Configuration
@RequiredArgsConstructor
public class KafkaConfiguration {

  private final ObjectMapper objectMapper;
  private final KafkaProperties kafkaProperties;
  private final RetryConfigurationProperties retryConfiguration;

  /**
   * Creates and configures {@link ConcurrentKafkaListenerContainerFactory} as Spring bean for consuming resource events
   * from Apache Kafka.
   *
   * @return {@link ConcurrentKafkaListenerContainerFactory} object as Spring bean.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ResourceEvent> kafkaListenerContainerFactory() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, ResourceEvent>();
    factory.setConsumerFactory(jsonNodeConsumerFactory());
    factory.setCommonErrorHandler(errorHandler(ResourceEvent.class));
    return factory;
  }

  /**
   * Creates and configures {@link ConcurrentKafkaListenerContainerFactory} as Spring bean for consuming
   * entitlement events from Apache Kafka.
   *
   * @return {@link ConcurrentKafkaListenerContainerFactory} object as Spring bean.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, EntitlementEvent> listenerContainerFactoryEntitlementEvent() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, EntitlementEvent>();
    factory.setConsumerFactory(consumerFactoryEntitlementEvent());
    factory.setCommonErrorHandler(errorHandler(EntitlementEvent.class));
    return factory;
  }

  /**
   * Creates and configures {@link ConsumerFactory} as Spring bean.
   *
   * <p>Key type - {@link String}, value - {@link ResourceEvent}.</p>
   *
   * @return typed {@link ConsumerFactory} object as Spring bean.
   */
  @Bean
  public ConsumerFactory<String, ResourceEvent> jsonNodeConsumerFactory() {
    return getConsumerFactory(ResourceEvent.class);
  }

  /**
   * Creates and configures {@link ConsumerFactory} as Spring bean.
   *
   * <p>Key type - {@link String}, value - {@link EntitlementEvent}.</p>
   *
   * @return typed {@link ConsumerFactory} object as Spring bean.
   */
  @Bean
  public ConsumerFactory<String, EntitlementEvent> consumerFactoryEntitlementEvent() {
    return getConsumerFactory(EntitlementEvent.class);
  }

  @Bean
  public TimerTableCheckService timerTableCheckService(JdbcTemplate jdbcTemplate, FolioExecutionContext context) {
    return new TimerTableCheckService(jdbcTemplate, context);
  }

  private <T> DefaultKafkaConsumerFactory<String, T> getConsumerFactory(Class<T> eventClass) {
    var deserializer = new JsonDeserializer<>(eventClass, objectMapper);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    config.put(AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  private DefaultErrorHandler errorHandler(Class<?> eventClass) {
    var errorHandler = new DefaultErrorHandler((message, exception) ->
      log.error("Failed to process event [record: {}]", message, exception));
    errorHandler.setBackOffFunction((message, exception) -> getBackOff(exception, eventClass));
    errorHandler.setLogLevel(Level.INFO);

    return errorHandler;
  }

  private BackOff getBackOff(Exception exception, Class<?> eventClass) {
    log.info("Calculating backoff for exception: exception = {}, eventClass = {}",
      exception.getMessage(), eventClass.getSimpleName(), exception);

    var relationDoesNotExistsMessage = findRelationDoesNotExistsMessage(exception);
    if (relationDoesNotExistsMessage.isPresent()) {
      var retryProperties = getRetryProperties(eventClass);
      log.warn("Tenant table is not found, retrying until created [message: {}]", relationDoesNotExistsMessage.get());
      return new FixedBackOff(retryProperties.getRetryDelay().toMillis(), retryProperties.getRetryAttempts());
    }

    return new FixedBackOff(0L, 0L);
  }

  private RetryConfigurationProperties.RetryProperties getRetryProperties(Class<?> eventClass) {
    var propertyKey = eventClass == EntitlementEvent.class
      ? "entitlement-event"
      : "scheduled-timer-event";
    return retryConfiguration.getConfig().get(propertyKey);
  }

  private static Optional<String> findRelationDoesNotExistsMessage(Exception exception) {
    return Optional.of(exception)
      .filter(InvalidDataAccessResourceUsageException.class::isInstance)
      .map(Throwable::getCause)
      .filter(SQLGrammarException.class::isInstance)
      .map(Throwable::getCause)
      .filter(throwable -> StringUtils.equals(throwable.getClass().getSimpleName(), "PSQLException"))
      .map(Throwable::getMessage)
      .filter(errorMessage -> errorMessage.startsWith("ERROR: relation") && errorMessage.contains("does not exist"))
      .map(errorMessage -> errorMessage.replaceAll("\\s+", " "));
  }
}
