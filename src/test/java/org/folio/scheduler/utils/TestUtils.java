package org.folio.scheduler.utils;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.TWO_HUNDRED_MILLISECONDS;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.shaded.org.awaitility.core.ConditionFactory;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestUtils {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .setSerializationInclusion(Include.NON_NULL)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

  @SneakyThrows
  public static String asJsonString(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }

  @SneakyThrows
  public static <T> T parse(String value, Class<T> type) {
    return OBJECT_MAPPER.readValue(value, type);
  }

  @SneakyThrows
  public static <T> T parseResponse(MvcResult result, Class<T> type) {
    return OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), type);
  }

  @SneakyThrows
  public static <T> T parseResponse(MvcResult result, TypeReference<T> type) {
    return OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), type);
  }

  @SneakyThrows
  public static String readString(String path) {
    try (var resource = TestUtils.class.getClassLoader().getResourceAsStream(path)) {
      return IOUtils.toString(resource, StandardCharsets.UTF_8);
    }
  }

  @SneakyThrows
  public static <T> T convertValue(Object value, TypeReference<T> toValueTypeRef) {
    return OBJECT_MAPPER.convertValue(value, toValueTypeRef);
  }

  @SneakyThrows
  public static <T> T convertValue(Object value, Class<T> clazz) {
    return OBJECT_MAPPER.convertValue(value, clazz);
  }

  public static void cleanUpCaches(CacheManager cacheManager) {
    cacheManager.getCacheNames().forEach(name -> requireNonNull(cacheManager.getCache(name)).clear());
  }

  public static ConditionFactory await() {
    return Awaitility.await().atMost(ONE_MINUTE).pollInterval(TWO_HUNDRED_MILLISECONDS);
  }

  /**
   * Sonar friendly Thread.sleep(millis) implementation
   *
   * @param duration - duration to await.
   */
  @SuppressWarnings({"SameParameterValue"})
  public static void awaitFor(Duration duration) {
    var sampleResult = Optional.of(1);
    Awaitility.await()
      .pollInSameThread()
      .atMost(duration.plus(Duration.ofMillis(250)))
      .pollDelay(duration)
      .untilAsserted(() -> assertThat(sampleResult).isPresent());
  }
}
