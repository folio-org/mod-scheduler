package org.folio.scheduler.domain.entity;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.model.TimerType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@NoArgsConstructor
@Table(name = "timer")
public class TimerDescriptorEntity extends Auditable {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type", columnDefinition = "timer_type")
  private TimerType type;

  private String moduleName;

  private String moduleId;

  private String naturalKey;

  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb", name = "timer_descriptor")
  private TimerDescriptor timerDescriptor;

  public void setTimerDescriptor(TimerDescriptor timerDescriptor) {
    this.timerDescriptor = timerDescriptor;
    this.naturalKey = toNaturalKey(timerDescriptor);
  }

  public static String toNaturalKey(TimerDescriptor td) {
    RoutingEntry re = td.getRoutingEntry();
    if (re == null) {
      return null;
    }

    if (isEmpty(td.getModuleName())) {
      throw new IllegalArgumentException("Module name is required");
    }

    var methods = re.getMethods() != null ? String.join(",", re.getMethods()) : "";
    var path = re.getPath() != null ? re.getPath() : re.getPathPattern();
    return String.format("%s#%s#%s#%s", td.getType(), td.getModuleName(), methods, path);
  }
}
