package org.folio.scheduler.repository;

import java.util.UUID;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SchedulerTimerRepository extends JpaRepository<TimerDescriptorEntity, UUID> {
}
