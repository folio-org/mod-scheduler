package org.folio.scheduler.integration.kafka.model;

import static com.github.jknack.handlebars.internal.lang3.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.stream.Stream;
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

  @JsonCreator
  public static EntitlementEventType of(String value) {
    if (isBlank(value)) {
      return null;
    }
    return Stream.of(values()).filter(val -> val.getValue().equalsIgnoreCase(value)).findAny().orElse(null);
  }
}

