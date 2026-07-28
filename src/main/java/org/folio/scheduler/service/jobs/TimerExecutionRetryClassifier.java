package org.folio.scheduler.service.jobs;

import static org.folio.scheduler.service.jobs.TimerExecutionRetryClassifier.RetryReason.AUTHORIZATION_SERVICE_UNAVAILABLE;
import static org.folio.scheduler.service.jobs.TimerExecutionRetryClassifier.RetryReason.CONNECTION_POOL_TIMEOUT;
import static org.folio.scheduler.service.jobs.TimerExecutionRetryClassifier.RetryReason.CONNECTION_REFUSED;
import static org.folio.scheduler.service.jobs.TimerExecutionRetryClassifier.RetryReason.CONNECT_TIMEOUT;

import java.net.ConnectException;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Decides whether a failed timer HTTP call may be retried.
 *
 * <p>This is an <b>allowlist</b>: only the failures enumerated here are retried, anything else is treated as
 * permanent. Timer handlers are predominantly {@code POST} and some of them charge fees or send patron notices, so an
 * unrecognised failure must not be repeated.</p>
 */
@Component
@RequiredArgsConstructor
public class TimerExecutionRetryClassifier {

  private static final String AUTHORIZATION_ERROR = "authorization_error";

  private final ObjectMapper objectMapper;

  /**
   * Tells whether the given timer execution failure is transient and worth retrying.
   *
   * @param throwable - failure thrown by the module HTTP call
   * @return {@code true} if the call may be retried, {@code false} otherwise
   */
  public boolean isRetryable(Throwable throwable) {
    return classify(throwable) != null;
  }

  /**
   * Classifies a failure to name the retry cause in logs.
   *
   * @param throwable failed timer call
   * @return matched retry reason, or {@code null} when the failure is not retryable
   */
  public RetryReason classify(Throwable throwable) {
    if (throwable == null) {
      return null;
    }

    if (throwable instanceof HttpStatusCodeException statusException) {
      return classifyStatusFailure(statusException);
    }

    return classifyTransportFailure(unwrap(throwable));
  }

  private RetryReason classifyStatusFailure(HttpStatusCodeException exception) {
    if (exception.getStatusCode().is5xxServerError() && hasErrorCode(exception, AUTHORIZATION_ERROR)) {
      return AUTHORIZATION_SERVICE_UNAVAILABLE;
    }
    return null;
  }

  private static RetryReason classifyTransportFailure(Throwable cause) {
    if (cause instanceof ConnectTimeoutException) {
      return CONNECT_TIMEOUT;
    }
    if (cause instanceof ConnectionRequestTimeoutException) {
      return CONNECTION_POOL_TIMEOUT;
    }
    if (cause instanceof ConnectException) {
      return CONNECTION_REFUSED;
    }
    return null;
  }

  private boolean hasErrorCode(HttpStatusCodeException exception, String expectedCode) {
    try {
      var errors = objectMapper.readTree(exception.getResponseBodyAsByteArray()).path("errors");
      if (errors.isArray()) {
        for (var error : errors) {
          if (expectedCode.equals(error.path("code").asString(null))) {
            return true;
          }
        }
      }
    } catch (JacksonException ignored) {
      // Malformed or absent error details are intentionally non-retryable.
    }
    return false;
  }

  /**
   * Unwraps the transport exception that Spring wraps into a {@link RestClientException}.
   */
  private static Throwable unwrap(Throwable throwable) {
    if (throwable instanceof RestClientException && throwable.getCause() != null) {
      return throwable.getCause();
    }
    return throwable;
  }

  public enum RetryReason {
    AUTHORIZATION_SERVICE_UNAVAILABLE,
    CONNECTION_REFUSED,
    CONNECT_TIMEOUT,
    CONNECTION_POOL_TIMEOUT
  }
}
