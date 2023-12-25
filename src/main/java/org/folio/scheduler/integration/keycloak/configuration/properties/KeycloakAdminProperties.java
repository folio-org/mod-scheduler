package org.folio.scheduler.integration.keycloak.configuration.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties
public class KeycloakAdminProperties {

  @NotBlank
  private String clientId;
}
