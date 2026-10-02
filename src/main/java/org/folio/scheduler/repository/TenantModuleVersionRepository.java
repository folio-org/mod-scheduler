package org.folio.scheduler.repository;

import jakarta.transaction.Transactional;
import java.util.Optional;
import org.folio.scheduler.domain.entity.TenantModuleVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantModuleVersionRepository extends JpaRepository<TenantModuleVersionEntity, Integer> {

  @Transactional
  @Modifying
  @Query(value = "INSERT INTO tenant_module_version (id, module_id) VALUES (1, :moduleId) "
    + "ON CONFLICT (id) DO UPDATE SET module_id = EXCLUDED.module_id, updated_date = CURRENT_TIMESTAMP",
    nativeQuery = true)
  void upsert(@Param("moduleId") String moduleId);

  @Query("SELECT e.moduleId FROM TenantModuleVersionEntity e WHERE e.id = 1")
  Optional<String> findModuleId();

  @Transactional
  @Modifying
  @Query("DELETE FROM TenantModuleVersionEntity")
  void deleteOwner();

  @Query(value = "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
    + "WHERE table_schema = :schema AND table_name = :table)",
    nativeQuery = true)
  boolean tableExists(@Param("schema") String schema, @Param("table") String table);
}
