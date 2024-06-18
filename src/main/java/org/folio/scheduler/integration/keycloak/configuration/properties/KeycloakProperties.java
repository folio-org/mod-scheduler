package org.folio.scheduler.integration.keycloak.configuration.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.folio.common.configuration.properties.TlsProperties;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "application.keycloak")
public class KeycloakProperties {

  @URL
  private String baseUrl;
  @NotBlank
  private String impersonationClient;
  @NestedConfigurationProperty
  private KeycloakAdminProperties admin;
  @NestedConfigurationProperty
  private TlsProperties tls;
}
