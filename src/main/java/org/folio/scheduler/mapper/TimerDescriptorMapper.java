package org.folio.scheduler.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;

@Mapper(componentModel = "spring", injectionStrategy = CONSTRUCTOR, uses = DateConvertHelper.class)
public interface TimerDescriptorMapper {

  /**
   * Converts {@link TimerDescriptor} to {@link TimerDescriptorEntity} object.
   *
   * @param descriptor - {@link TimerDescriptor} object
   * @return converted {@link TimerDescriptorEntity} object
   */
  @Mapping(target = "naturalKey", ignore = true)
  @Mapping(target = "timerDescriptor", source = "descriptor")
  @Mapping(target = "createdDate", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "updatedDate", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  TimerDescriptorEntity toDescriptorEntity(TimerDescriptor descriptor);

  @AuditableMapping
  @Mapping(target = "routingEntry", ignore = true)
  @Mapping(target = "modified", ignore = true)
  @Mapping(target = "enabled", ignore = true)
  @Mapping(target = "moduleName", ignore = true)
  @Mapping(target = "moduleId", ignore = true)
  TimerDescriptor toDescriptor(TimerDescriptorEntity entity);

  @ObjectFactory
  default TimerDescriptor createDescriptor(TimerDescriptorEntity entity) {
    return entity.getTimerDescriptor();
  }
}
