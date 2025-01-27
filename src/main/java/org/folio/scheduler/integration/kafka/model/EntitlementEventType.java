package org.folio.scheduler.integration.kafka.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EntitlementEventType {

  ENTITLE("entitle"),
  REVOKE("revoke"),
  UPGRADE("upgrade");

  @JsonValue
  private final String value;
}

