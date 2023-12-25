package org.folio.scheduler.service;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.domain.model.SearchResult;
import org.folio.scheduler.exception.RequestValidationException;
import org.folio.scheduler.mapper.TimerDescriptorMapper;
import org.folio.scheduler.repository.SchedulerTimerRepository;
import org.folio.spring.data.OffsetRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class SchedulerTimerService {

  private final TimerDescriptorMapper timerDescriptorMapper;
  private final JobSchedulingService jobSchedulingService;
  private final SchedulerTimerRepository schedulerTimerRepository;

  /**
   * Returns {@link Optional} of {@link TimerDescriptor} object by id.
   *
   * @param uuid - timer descriptor id as {@link UUID} object
   * @return found {@link TimerDescriptor} object in {@link Optional} wrapper, it will be empty if value is not found.
   */
  @Transactional(readOnly = true)
  public Optional<TimerDescriptor> findById(UUID uuid) {
    return schedulerTimerRepository.findById(uuid).map(TimerDescriptorEntity::getTimerDescriptor);
  }

  /**
   * Returns {@link TimerDescriptor} object by id.
   *
   * @param uuid - timer descriptor id as {@link UUID} object
   * @return found {@link TimerDescriptor} object.
   * @throws EntityNotFoundException if timer descriptor is not found by id.
   */
  @Transactional(readOnly = true)
  public TimerDescriptor getById(UUID uuid) {
    return findById(uuid).orElseThrow(() ->
      new EntityNotFoundException("Unable to find TimerDescriptor with id " + uuid));
  }

  /**
   * Retrieves all timer descriptors.
   *
   * @return saved {@link TimerDescriptor} object
   */
  @Transactional(readOnly = true)
  public SearchResult<TimerDescriptor> getAll(Integer offset, Integer limit) {
    return SearchResult.of(schedulerTimerRepository.findAll(OffsetRequest.of(offset, limit)).stream()
      .map(TimerDescriptorEntity::getTimerDescriptor)
      .collect(Collectors.toList()));
  }

  /**
   * Saves timer descriptor.
   *
   * @param timerDescriptor - timer descriptor object to save.
   * @return saved {@link TimerDescriptor} object
   */
  @Transactional
  public TimerDescriptor create(TimerDescriptor timerDescriptor) {
    var id = timerDescriptor.getId();
    if (id != null) {
      var entityById = schedulerTimerRepository.findById(id);
      if (entityById.isPresent()) {
        throw new EntityExistsException("TimerDescriptor already exist for id " + id);
      }
    }

    timerDescriptor.setId(defaultIfNull(id, UUID.randomUUID()));
    var entity = timerDescriptorMapper.convert(timerDescriptor);
    var savedEntity = schedulerTimerRepository.save(entity);
    jobSchedulingService.schedule(timerDescriptor);

    return savedEntity.getTimerDescriptor();
  }

  /**
   * Updates timer descriptor by id.
   *
   * @param uuid - timer descriptor id.
   * @param newDescriptor - timer descriptor data to update
   * @return updated {@link TimerDescriptor} object
   * @throws EntityNotFoundException if timer descriptor is not found by id.
   */
  @Transactional
  public TimerDescriptor update(UUID uuid, TimerDescriptor newDescriptor) {
    if (newDescriptor.getId() == null) {
      throw new RequestValidationException("Timer descriptor id is required", "id", "null");
    }

    if (!Objects.equals(uuid, newDescriptor.getId())) {
      throw new RequestValidationException("Id in the url and in the entity must match", "id", "not matched");
    }

    var oldTimerDescriptor = schedulerTimerRepository.findById(uuid)
      .map(TimerDescriptorEntity::getTimerDescriptor)
      .orElseThrow(() -> new EntityNotFoundException("Unable to find timer descriptor with id " + uuid));

    newDescriptor.modified(true);
    var convertedValue = timerDescriptorMapper.convert(newDescriptor);
    var updatedEntity = schedulerTimerRepository.save(convertedValue);
    var timerDescriptor = updatedEntity.getTimerDescriptor();
    jobSchedulingService.reschedule(oldTimerDescriptor, timerDescriptor);

    return timerDescriptor;
  }

  /**
   * Deletes timer descriptor by id.
   *
   * @param id - timer descriptor id
   */
  @Transactional
  public void delete(UUID id) {
    schedulerTimerRepository.findById(id).ifPresent(entity -> {
      schedulerTimerRepository.delete(entity);
      jobSchedulingService.delete(entity.getTimerDescriptor());
    });
  }

  /**
   * Deletes all scheduled timers, assigned to tenant.
   */
  @Transactional
  public void deleteAll() {
    var allEntities = schedulerTimerRepository.findAll();
    for (var timerDescriptorEntity : allEntities) {
      schedulerTimerRepository.delete(timerDescriptorEntity);
      jobSchedulingService.delete(timerDescriptorEntity.getTimerDescriptor());
    }
  }
}
