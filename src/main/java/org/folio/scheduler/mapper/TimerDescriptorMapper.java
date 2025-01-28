package org.folio.scheduler.mapper;

import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TimerDescriptorMapper {

  /**
   * Converts {@link TimerDescriptor} to {@link TimerDescriptorEntity} object.
   *
   * @param descriptor - {@link TimerDescriptor} object
   * @return converted {@link TimerDescriptorEntity} object
   */
  @Mapping(target = "naturalKey", ignore = true)
  @Mapping(target = "timerDescriptor", source = "descriptor")
  TimerDescriptorEntity convert(TimerDescriptor descriptor);
}
