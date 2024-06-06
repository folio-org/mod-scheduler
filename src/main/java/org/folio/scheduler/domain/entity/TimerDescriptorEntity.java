package org.folio.scheduler.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.hibernate.annotations.Type;

@Data
@Entity
@NoArgsConstructor
@Table(name = "timer")
public class TimerDescriptorEntity {

  @Id
  private UUID id;

  private String moduleName;

  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb", name = "timer_descriptor")
  private TimerDescriptor timerDescriptor;

  public static TimerDescriptorEntity of(TimerDescriptor timerDescriptor) {
    var entity = new TimerDescriptorEntity();
    entity.id = timerDescriptor.getId();
    entity.timerDescriptor = timerDescriptor;
    return entity;
  }
}
