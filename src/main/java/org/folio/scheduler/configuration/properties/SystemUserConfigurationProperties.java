package org.folio.scheduler.configuration.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("application.system.user")
public class SystemUserConfigurationProperties {

  /**
   * System username template.
   *
   * <p>
   * Allowed placeholders:
   * <ul>
   *   <li>{tenantId} - tenant identifier as name from {@link org.folio.spring.FolioExecutionContext}</li>
   * </ul>
   * </p>
   */
  private String usernameTemplate = "{tenantId}-system-user";
}
