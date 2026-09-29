package org.folio.scheduler.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Entity
@NoArgsConstructor
@Table(name = "tenant_module_version")
public class TenantModuleVersionEntity {

  @Id private Integer id;

  private String moduleId;
}
