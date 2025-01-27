package org.folio.scheduler.repository;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.domain.model.TimerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SchedulerTimerRepository extends JpaRepository<TimerDescriptorEntity, UUID> {

  List<TimerDescriptorEntity> findByModuleNameAndType(String moduleName, TimerType type);

  Optional<TimerDescriptorEntity> findByNaturalKey(String naturalKey);

  @Transactional
  @Modifying
  @Query(value = "UPDATE timer "
    + "SET timer_descriptor = jsonb_set(timer_descriptor, '{enabled}', to_jsonb(:enable)) "
    + "WHERE module_name = :moduleName AND type = CAST(:timerType as timer_type) "
    + "AND COALESCE((timer_descriptor ->> 'enabled')::boolean, false) != :enable",
    nativeQuery = true)
  int switchTimersByModuleNameAndType(String moduleName, String timerType, boolean enable);
}
