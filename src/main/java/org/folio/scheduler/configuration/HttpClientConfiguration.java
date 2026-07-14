package org.folio.scheduler.configuration;

import org.folio.scheduler.integration.OkapiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfiguration {

  @Bean
  public OkapiClient okapiClient(HttpServiceProxyFactory factory) {
    return factory.createClient(OkapiClient.class);
  }
}
