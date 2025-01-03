package org.folio.scheduler.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.domain.model.TimerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SchedulerTimerRepository extends JpaRepository<TimerDescriptorEntity, UUID> {

  List<TimerDescriptorEntity> findByModuleNameAndType(String moduleName, TimerType type);

  Optional<TimerDescriptorEntity> findByNaturalKey(String naturalKey);
}
