package org.folio.scheduler.domain.entity;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.model.TimerType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@ToString(callSuper = true)
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

  @Override
  public final boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    var objEffectiveClass = obj instanceof HibernateProxy
      ? ((HibernateProxy) obj).getHibernateLazyInitializer().getPersistentClass() : obj.getClass();
    var thisEffectiveClass = this instanceof HibernateProxy
      ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();

    if (thisEffectiveClass != objEffectiveClass) {
      return false;
    }

    var that = (TimerDescriptorEntity) obj;
    return getId() != null && Objects.equals(getId(), that.getId());
  }

  @Override
  public final int hashCode() {
    return this instanceof HibernateProxy
      ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
  }
}
