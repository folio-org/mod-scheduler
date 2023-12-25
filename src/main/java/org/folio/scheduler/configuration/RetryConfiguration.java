package org.folio.scheduler.configuration;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.listener.RetryListenerSupport;

@Log4j2
@EnableRetry
@Configuration
public class RetryConfiguration {

  @Bean(name = "methodLoggingRetryListener")
  public RetryListener methodLoggingRetryListener() {
    return new RetryListenerSupport() {

      @Override
      public <T, E extends Throwable> void onError(RetryContext ctx, RetryCallback<T, E> callback, Throwable t) {
        log.warn("Retryable method '{}' threw {}th exception with message: {}",
          ctx.getAttribute("context.name"), ctx.getRetryCount(), t.toString());
      }
    };
  }
}
