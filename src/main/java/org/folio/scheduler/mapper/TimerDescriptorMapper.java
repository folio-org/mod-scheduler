package org.folio.scheduler.mapper;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import org.folio.common.utils.SemverUtils;
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
  @Mapping(target = "moduleName", expression = "java(fromModuleId(descriptor))")
  TimerDescriptorEntity convert(TimerDescriptor descriptor);

  default String fromModuleId(TimerDescriptor descriptor) {
    var moduleId = descriptor.getModuleId();
    return isNotEmpty(moduleId) ? SemverUtils.getName(moduleId) : descriptor.getModuleName();
  }
}
