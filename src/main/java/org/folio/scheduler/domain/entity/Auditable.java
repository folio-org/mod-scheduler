package org.folio.scheduler.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Auditable is a mapped superclass that contains fields for auditing and the corresponding annotations
 * for JPA auditing. All audit fields are automatically populated by Spring Data JPA.
 */
@Data
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class Auditable {

  /**
   * Indicates the date the entity was created.
   */
  @CreatedDate
  @Column(name = "created_date", nullable = false, updatable = false)
  private OffsetDateTime createdDate;

  /**
   * Indicates the user who created the entity.
   */
  @CreatedBy
  @Column(name = "created_by_user_id", updatable = false)
  private UUID createdByUserId;

  /**
   * Indicates the date the entity was last modified.
   * IMPORTANT: This field is NOT NULL and automatically populated on creation with the same value as createdDate.
   */
  @LastModifiedDate
  @Column(name = "updated_date", nullable = false)
  private OffsetDateTime updatedDate;

  /**
   * Indicates the user who last modified the entity.
   */
  @LastModifiedBy
  @Column(name = "updated_by_user_id")
  private UUID updatedByUserId;
}
