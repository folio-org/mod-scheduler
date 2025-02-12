package org.folio.scheduler.integration.kafka.model;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class EntitlementEvent {

  private EntitlementEventType type;
  private String moduleId;
  private String tenantName;
  private UUID tenantId;
}
