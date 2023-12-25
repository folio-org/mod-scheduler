package org.folio.scheduler.mapper;

import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TimerDescriptorMapper {

  /**
   * Converts {@link TimerDescriptorEntity} to {@link TimerDescriptor} object.
   *
   * @param entity - {@link TimerDescriptorEntity} object
   * @return converted {@link TimerDescriptor} object
   */
  TimerDescriptor convert(TimerDescriptorEntity entity);

  /**
   * Converts {@link TimerDescriptor} to {@link TimerDescriptorEntity} object.
   *
   * @param descriptor - {@link TimerDescriptor} object
   * @return converted {@link TimerDescriptorEntity} object
   */
  @Mapping(target = "timerDescriptor", source = "descriptor")
  TimerDescriptorEntity convert(TimerDescriptor descriptor);
}
