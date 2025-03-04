package org.folio.scheduler.repository;

import jakarta.transaction.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.domain.model.TimerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SchedulerTimerRepository extends JpaRepository<TimerDescriptorEntity, UUID> {

  List<TimerDescriptorEntity> findByModuleNameAndType(String moduleName, TimerType type);

  Optional<TimerDescriptorEntity> findByNaturalKey(String naturalKey);

  @Query(value = "SELECT * FROM timer "
    + "WHERE module_name = :moduleName "
    + "AND COALESCE((timer_descriptor ->> 'enabled')::boolean, false) != :enabled",
    nativeQuery = true)
  List<TimerDescriptorEntity> findByModuleNameAndEnabledState(@Param("moduleName") String moduleName,
    @Param("enabled") boolean enabled);

  @Transactional
  @Modifying
  @Query(value = "UPDATE timer "
    + "SET timer_descriptor = jsonb_set(timer_descriptor, '{enabled}', to_jsonb(:enabled)) "
    + "WHERE id in (:ids)",
    nativeQuery = true)
  void switchTimersByIds(@Param("ids") Collection<UUID> ids, @Param("enabled") boolean enabled);
}
