package org.folio.scheduler.configuration;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.folio.scheduler.integration.OkapiClient;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.NotFoundRestClientAdapterDecorator;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfiguration {

  /**
   * Builds {@link OkapiClient} with Apache HttpClient's transport-level retries disabled, so that the timer retry
   * policy is the only one repeating a request. The shared folio builder is cloned rather than mutated to keep other
   * folio clients unaffected.
   */
  @Bean
  public OkapiClient okapiClient(RestClient.Builder folioRestClientBuilder) {
    var requestFactory = ClientHttpRequestFactoryBuilder.httpComponents()
      .withHttpClientCustomizer(HttpClientBuilder::disableAutomaticRetries)
      .build(HttpClientSettings.defaults());
    var restClient = folioRestClientBuilder.clone()
      .requestFactory(requestFactory)
      .build();

    return HttpServiceProxyFactory
      .builderFor(RestClientAdapter.create(restClient))
      .exchangeAdapterDecorator(NotFoundRestClientAdapterDecorator::new)
      .build()
      .createClient(OkapiClient.class);
  }
}
