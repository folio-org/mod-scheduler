package org.folio.scheduler.integration.kafka.configuration;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Strings;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.integration.kafka.consumer.EnableKafkaConsumer;
import org.folio.integration.kafka.consumer.filter.TenantIsDisabledException;
import org.folio.integration.kafka.consumer.filter.TenantsAreDisabledException;
import org.folio.integration.kafka.model.ResourceEvent;
import org.folio.scheduler.configuration.properties.RetryConfigurationProperties;
import org.folio.scheduler.configuration.properties.RetryConfigurationProperties.RetryProperties;
import org.folio.scheduler.integration.kafka.TimerTableCheckService;
import org.folio.scheduler.integration.kafka.model.EntitlementEvent;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.exception.LiquibaseMigrationException;
import org.hibernate.exception.SQLGrammarException;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.KafkaException.Level;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ObjectMapper;

@Log4j2
@Configuration
@EnableKafkaConsumer
@RequiredArgsConstructor
public class KafkaConfiguration {

  private final KafkaProperties kafkaProperties;
  private final RetryConfigurationProperties retryConfiguration;
  private final ObjectMapper objectMapper;

  /**
   * Creates and configures {@link ConcurrentKafkaListenerContainerFactory} as Spring bean for consuming resource events
   * from Apache Kafka.
   *
   * @return {@link ConcurrentKafkaListenerContainerFactory} object as Spring bean.
   */
  @Bean
  @SuppressWarnings("rawtypes")
  public ConcurrentKafkaListenerContainerFactory<String, ResourceEvent<?>> kafkaListenerContainerFactory() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, ResourceEvent<?>>();
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
    var deserializer = new JacksonJsonDeserializer<>(eventClass);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties());
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

  @SuppressWarnings("checkstyle:MethodLength")
  BackOff getBackOff(Exception exception, Class<?> eventClass) {
    log.info("Calculating backoff for exception: exception = {}, eventClass = {}",
      exception.getMessage(), eventClass.getSimpleName(), exception);

    if (hasCause(exception, LiquibaseMigrationException.class)) {
      var retryProperties = getRetryProperties(eventClass);
      log.warn("Liquibase migration in progress, retrying Kafka event", exception);
      return getFixedBackOff(retryProperties);
    }

    var relationDoesNotExistsMessage = findRelationDoesNotExistsMessage(exception);
    if (relationDoesNotExistsMessage.isPresent()) {
      var retryProperties = getRetryProperties(eventClass);
      log.warn("Tenant table is not found, retrying until created [message: {}]", relationDoesNotExistsMessage.get());
      return getFixedBackOff(retryProperties);
    }

    if (hasCause(exception, TenantsAreDisabledException.class)
      || hasCause(exception, TenantIsDisabledException.class)) {
      var retryProperties = getRetryProperties(eventClass);
      log.warn("Tenant(s) is disabled, retrying Kafka event", exception);
      return getFixedBackOff(retryProperties);
    }

    return new FixedBackOff(0L, 0L);
  }

  private RetryConfigurationProperties.RetryProperties getRetryProperties(Class<?> eventClass) {
    var propertyKey = eventClass == EntitlementEvent.class
      ? "entitlement-event"
      : "scheduled-timer-event";
    return retryConfiguration.getConfig().get(propertyKey);
  }

  private static @NonNull FixedBackOff getFixedBackOff(RetryProperties retryProperties) {
    return new FixedBackOff(retryProperties.getRetryDelay().toMillis(), retryProperties.getRetryAttempts());
  }

  private static boolean hasCause(Throwable throwable, Class<? extends Throwable> expectedType) {
    for (var current = throwable; current != null; current = current.getCause()) {
      if (expectedType.isInstance(current)) {
        return true;
      }
    }

    return false;
  }

  private static Optional<String> findRelationDoesNotExistsMessage(Exception exception) {
    return Optional.of(exception)
      .filter(InvalidDataAccessResourceUsageException.class::isInstance)
      .map(Throwable::getCause)
      .filter(SQLGrammarException.class::isInstance)
      .map(Throwable::getCause)
      .filter(throwable -> Strings.CS.equals(throwable.getClass().getSimpleName(), "PSQLException"))
      .map(Throwable::getMessage)
      .filter(errorMessage -> errorMessage.startsWith("ERROR: relation") && errorMessage.contains("does not exist"))
      .map(errorMessage -> errorMessage.replaceAll("\\s+", " "));
  }
}
