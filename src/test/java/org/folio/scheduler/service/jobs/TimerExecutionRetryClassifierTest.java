package org.folio.scheduler.service.jobs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.scheduler.utils.TestUtils.OBJECT_MAPPER;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import javax.net.ssl.SSLHandshakeException;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@UnitTest
class TimerExecutionRetryClassifierTest {

  private final TimerExecutionRetryClassifier classifier = new TimerExecutionRetryClassifier(OBJECT_MAPPER);

  @ParameterizedTest
  @ValueSource(ints = {500, 502, 503, 504, 599})
  void isRetryable_positive_returnsTrueForAuthorizationErrorServerFailure(int status) {
    var responseBody = """
      {"errors":[{"type":"AuthorizationException","code":"authorization_error","message":"Unavailable"}],
       "total_records":1}
      """;

    assertThat(classifier.isRetryable(serverError(status, responseBody))).isTrue();
  }

  @Test
  void isRetryable_positive_returnsTrueWhenRetryCodeIsNotFirstInErrors() {
    var responseBody = """
      {"errors":[{"code":"service_unavailable"},{"code":"authorization_error"}],"total_records":2}
      """;

    assertThat(classifier.isRetryable(serverError(503, responseBody))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 404, 408, 429})
  void isRetryable_negative_returnsFalseForClientErrors(int status) {
    assertThat(classifier.isRetryable(clientError(status, ""))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 502, 503, 504, 599})
  void isRetryable_negative_returnsFalseForGenericServerErrors(int status) {
    assertThat(classifier.isRetryable(serverError(status, ""))).isFalse();
  }

  @Test
  void isRetryable_negative_returnsFalseForTenantNotEnabledBadRequest() {
    var responseBody = """
      {"errors":[{"type":"TenantNotEnabledException","code":"tenant_not_enabled","message":"Tenant is disabled"}],
       "total_records":1}
      """;

    assertThat(classifier.isRetryable(clientError(400, responseBody))).isFalse();
  }

  @Test
  void isRetryable_negative_returnsFalseForOtherServerErrorCode() {
    var responseBody = """
      {"errors":[{"code":"service_unavailable"}],"total_records":1}
      """;

    assertThat(classifier.isRetryable(serverError(503, responseBody))).isFalse();
  }

  @Test
  void isRetryable_negative_returnsFalseForMalformedResponseBody() {
    assertThat(classifier.isRetryable(serverError(503, "not-json"))).isFalse();
  }

  @ParameterizedTest
  @MethodSource("retryableTransportFailures")
  void isRetryable_positive_returnsTrueForTransportFailureBeforeRequestIsSent(IOException cause) {
    assertThat(classifier.isRetryable(transportFailure(cause))).isTrue();
  }

  private static Stream<IOException> retryableTransportFailures() {
    return Stream.of(
      new ConnectTimeoutException("connect timed out"),
      new ConnectException("Connection refused"),
      new ConnectionRequestTimeoutException("connection pool timed out"));
  }

  /**
   * A read timeout, an unknown host and a TLS failure are all {@link IOException}s that must stay non-retryable.
   */
  @ParameterizedTest
  @MethodSource("nonRetryableTransportFailures")
  void isRetryable_negative_returnsFalseForOtherTransportFailure(IOException cause) {
    assertThat(classifier.isRetryable(transportFailure(cause))).isFalse();
  }

  private static Stream<IOException> nonRetryableTransportFailures() {
    return Stream.of(
      new IOException("broken pipe"),
      new SocketTimeoutException("Read timed out"),
      new UnknownHostException("no-such-host"),
      new SSLHandshakeException("handshake failed"));
  }

  @ParameterizedTest
  @NullSource
  @MethodSource("unrecognizedFailures")
  void isRetryable_negative_returnsFalseForUnrecognizedFailure(Throwable throwable) {
    assertThat(classifier.isRetryable(throwable)).isFalse();
  }

  private static Stream<Throwable> unrecognizedFailures() {
    return Stream.of(
      new RestClientException("no cause"),
      new IllegalStateException("pre-flight failure"));
  }

  private static RestClientException clientError(int status, String responseBody) {
    var statusText = HttpStatus.resolve(status) == null ? "Custom" : HttpStatus.valueOf(status).getReasonPhrase();
    return HttpClientErrorException.create(
      HttpStatusCode.valueOf(status), statusText, new HttpHeaders(), responseBody.getBytes(UTF_8), UTF_8);
  }

  private static RestClientException serverError(int status, String responseBody) {
    var statusText = HttpStatus.resolve(status) == null ? "Custom" : HttpStatus.valueOf(status).getReasonPhrase();
    return HttpServerErrorException.create(
      HttpStatusCode.valueOf(status), statusText, new HttpHeaders(), responseBody.getBytes(UTF_8), UTF_8);
  }

  private static RestClientException transportFailure(IOException cause) {
    return new ResourceAccessException("I/O error on POST request", cause);
  }
}
