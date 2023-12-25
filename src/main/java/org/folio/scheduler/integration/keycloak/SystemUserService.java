package org.folio.scheduler.integration.keycloak;

import lombok.RequiredArgsConstructor;
import org.folio.scheduler.configuration.properties.SystemUserConfigurationProperties;
import org.folio.spring.exception.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemUserService {

  private final KeycloakUserService keycloakUserService;
  private final SystemUserConfigurationProperties systemUserProperties;

  /**
   * Retrieves system user id by username from keycloak.
   *
   * @param tenant - tenant identifier.
   * @return system user identifier
   */
  @Retryable(
    maxAttemptsExpression = "#{@retryConfigurationProperties.config['system-user'].retryAttempts}",
    backoff = @Backoff(
      delayExpression = "#{@retryConfigurationProperties.config['system-user'].retryDelay.toMillis()}",
      maxDelayExpression = "#{@retryConfigurationProperties.config['system-user'].maxDelay.toMillis()}",
      multiplierExpression = "#{@retryConfigurationProperties.config['system-user'].retryMultiplier}"
    ),
    retryFor = {NotFoundException.class},
    listeners = "methodLoggingRetryListener")
  @Cacheable(cacheNames = "system-user-id", key = "#tenant")
  public String findSystemUserId(String tenant) {
    var usernameTemplate = systemUserProperties.getUsernameTemplate();
    var username = generateValueByTemplate(usernameTemplate, tenant);
    return keycloakUserService.findUserIdByKeycloakUsername(tenant, username);
  }

  private String generateValueByTemplate(String template, String tenant) {
    return template.replace("{tenantId}", tenant);
  }
}
