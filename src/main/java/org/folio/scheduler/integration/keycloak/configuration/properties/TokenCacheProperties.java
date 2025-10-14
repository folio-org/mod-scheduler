package org.folio.scheduler.integration.keycloak.configuration.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "application.token-cache")
public class TokenCacheProperties {

  @Valid
  @NotNull
  @NestedConfigurationProperty
  private TokenCacheCapacityProperties capacity;
  /**
   * Specifies the amount of seconds for a cache entry invalidation prior to the token expiration.
   * The purpose of early cache entry expiration is to minimize a risk that a token expires
   * when a request is being processed.
   */
  @NotNull
  @Positive
  private Integer refreshBeforeExpirySec;
}
