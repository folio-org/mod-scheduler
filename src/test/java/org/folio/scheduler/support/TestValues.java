package org.folio.scheduler.support;

import static org.folio.scheduler.domain.dto.TimerUnit.SECOND;
import static org.folio.scheduler.support.TestConstants.MODULE_NAME;
import static org.folio.scheduler.support.TestConstants.TIMER_UUID;

import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.domain.model.TimerType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestValues {

  public static TimerDescriptor timerDescriptor() {
    return timerDescriptor(TIMER_UUID);
  }

  public static TimerDescriptor timerDescriptor(UUID uuid) {
    return new TimerDescriptor()
      .id(uuid)
      .enabled(true)
      .moduleName(MODULE_NAME)
      .routingEntry(new RoutingEntry()
        .methods(List.of("POST"))
        .pathPattern("/testb/timer/20")
        .unit(SECOND)
        .delay("20"));
  }

  public static TimerDescriptorEntity timerDescriptorEntity() {
    return timerDescriptorEntity(timerDescriptor());
  }

  public static TimerDescriptorEntity timerDescriptorEntity(TimerDescriptor descriptor) {
    var entity = new TimerDescriptorEntity();
    entity.setId(TIMER_UUID);
    entity.setType(toEntityTimeType(descriptor.getType()));
    entity.setTimerDescriptor(descriptor);
    entity.setModuleId(descriptor.getModuleId());
    entity.setModuleName(descriptor.getModuleName());
    return entity;
  }

  private static TimerType toEntityTimeType(org.folio.scheduler.domain.dto.TimerType timerType) {
    if (timerType == null) {
      return null;
    }

    return switch (timerType) {
      case USER -> TimerType.USER;
      case SYSTEM -> TimerType.SYSTEM;
    };
  }
}
