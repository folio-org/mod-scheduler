package org.folio.scheduler.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.hibernate.annotations.Type;

@Data
@Entity
@NoArgsConstructor
@Table(name = "timer")
public class TimerDescriptorEntity {

  @Id private UUID id;

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

  public static TimerDescriptorEntity of(TimerDescriptor timerDescriptor) {
    var entity = new TimerDescriptorEntity();
    entity.id = timerDescriptor.getId();
    entity.timerDescriptor = timerDescriptor;
    entity.naturalKey = toNaturalKey(timerDescriptor);
    return entity;
  }

  public static String toNaturalKey(TimerDescriptor timerDescriptor) {
    RoutingEntry re = timerDescriptor.getRoutingEntry();
    if (re == null) {
      return null;
    }

    var methods = re.getMethods() != null ? String.join(",", re.getMethods()) : "";
    var path = re.getPath() != null ? re.getPath() : re.getPathPattern();
    return String.format("%s#%s#%s", timerDescriptor.getModuleName() != null ? timerDescriptor.getModuleName() : "",
      methods, path);
  }
}
