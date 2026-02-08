package org.folio.scheduler.mapper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.mapstruct.Mapping;

/**
 * Reusable MapStruct annotation for mapping audit fields from entity to DTO's nested metadata object.
 * Maps entity's audit fields to TimerDescriptor.metadata.* fields.
 */
@Retention(RetentionPolicy.CLASS)
@Mapping(target = "metadata.createdDate", source = "entity.createdDate")
@Mapping(target = "metadata.createdByUserId", source = "entity.createdByUserId")
@Mapping(target = "metadata.updatedDate", source = "entity.updatedDate")
@Mapping(target = "metadata.updatedByUserId", source = "entity.updatedByUserId")
public @interface AuditableMapping {
}
