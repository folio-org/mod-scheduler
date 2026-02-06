package org.folio.scheduler.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import org.folio.scheduler.domain.dto.Metadata;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.RoutingEntrySchedule;
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

  /**
   * Factory method to create a deep copy of {@link TimerDescriptor} from {@link TimerDescriptorEntity}.
   *
   * @param entity - source {@link TimerDescriptorEntity} object
   * @return deep copy of the {@link TimerDescriptor} object
   */
  @ObjectFactory
  default TimerDescriptor createDescriptor(TimerDescriptorEntity entity) {
    return deepCopy(entity.getTimerDescriptor());
  }

  /**
   * Creates a deep copy of {@link TimerDescriptor} object.
   *
   * <p>This method is used to create a deep copy of the {@link TimerDescriptor} object
   * to ensure that any modifications made to the copied object do not affect the original object.
   * It is particularly useful when the {@link TimerDescriptor} contains mutable fields
   * or nested objects that need to be duplicated rather than referenced.
   *
   * <p>Caution: If a new complex object is added to the {@link TimerDescriptor} class,
   * this method must be updated to include the logic for deep copying that new object
   * by creating a respective deep copy method for it and invoking that method within this method.
   *
   * @param source - source {@link TimerDescriptor} object to copy
   * @return deep copy of the {@link TimerDescriptor} object
   */
  TimerDescriptor deepCopy(TimerDescriptor source);

  /**
   * Creates a deep copy of {@link RoutingEntry} object.
   *
   * @param source - source {@link RoutingEntry} object to copy
   * @return deep copy of the {@link RoutingEntry} object
   */
  RoutingEntry deepCopy(RoutingEntry source);

  /**
   * Creates a deep copy of {@link RoutingEntrySchedule} object.
   *
   * @param source - source {@link RoutingEntrySchedule} object to copy
   * @return deep copy of the {@link RoutingEntrySchedule} object
   */
  RoutingEntrySchedule deepCopy(RoutingEntrySchedule source);

  /**
   * Creates a deep copy of {@link Metadata} object.
   *
   * @param source - source {@link Metadata} object to copy
   * @return deep copy of the {@link Metadata} object
   */
  Metadata deepCopy(Metadata source);
}
