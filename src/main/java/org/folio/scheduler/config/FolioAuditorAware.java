package org.folio.scheduler.config;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.spring.FolioExecutionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configures Spring Data JPA auditing to automatically populate audit fields.
 * Retrieves the current user ID from FolioExecutionContext.
 */
@Configuration
@EnableJpaAuditing
@RequiredArgsConstructor
public class FolioAuditorAware implements AuditorAware<UUID> {

  private final FolioExecutionContext folioExecutionContext;

  @Override
  public Optional<UUID> getCurrentAuditor() {
    return Optional.ofNullable(folioExecutionContext.getUserId());
  }
}
