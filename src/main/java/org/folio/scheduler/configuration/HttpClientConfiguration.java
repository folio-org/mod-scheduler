package org.folio.scheduler.configuration;

import org.folio.scheduler.integration.ModuleClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfiguration {

  @Bean
  public ModuleClient okapiClient(HttpServiceProxyFactory factory) {
    return factory.createClient(ModuleClient.class);
  }
}
