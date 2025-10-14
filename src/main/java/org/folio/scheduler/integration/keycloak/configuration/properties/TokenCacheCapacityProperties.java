package org.folio.scheduler.integration.keycloak.configuration.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties
public class TokenCacheCapacityProperties {

  @NotNull
  @Positive
  private Integer initial;
  @NotNull
  @Positive
  private Integer max;
}
