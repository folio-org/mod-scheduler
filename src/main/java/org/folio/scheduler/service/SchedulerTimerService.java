package org.folio.scheduler.service;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.scheduler.utils.TimerDescriptorUtils.evalModuleName;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.domain.model.SearchResult;
import org.folio.scheduler.domain.model.TimerType;
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

  @Transactional(readOnly = true)
  public List<TimerDescriptor> findByModuleNameAndType(String moduleName, TimerType type) {
    return mapItems(schedulerTimerRepository.findByModuleNameAndType(moduleName, type),
      TimerDescriptorEntity::getTimerDescriptor);
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
    return schedulerTimerRepository.findById(uuid).map(TimerDescriptorEntity::getTimerDescriptor).orElseThrow(
      () -> new EntityNotFoundException("Unable to find TimerDescriptor with id " + uuid));
  }

  /**
   * Retrieves all timer descriptors.
   *
   * @return saved {@link TimerDescriptor} object
   */
  @Transactional(readOnly = true)
  public SearchResult<TimerDescriptor> getAll(Integer offset, Integer limit) {
    return SearchResult.of(schedulerTimerRepository.findAll(OffsetRequest.of(offset, limit)).stream()
      .map(TimerDescriptorEntity::getTimerDescriptor).toList());
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

    validate(timerDescriptor);

    timerDescriptor.setId(defaultIfNull(id, UUID.randomUUID()));
    timerDescriptor.setModuleName(evalModuleName(timerDescriptor));

    var naturalKey = TimerDescriptorEntity.toNaturalKey(timerDescriptor);
    return schedulerTimerRepository.findByNaturalKey(naturalKey)
      .map(existingTimer -> {
        timerDescriptor.setId(existingTimer.getId());
        return doUpdate(timerDescriptor);
      })
      .orElseGet(() -> doCreate(timerDescriptor));
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

    validate(newDescriptor);
    newDescriptor.setModuleName(evalModuleName(newDescriptor));

    return doUpdate(newDescriptor);
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

  /**
   * Switch module's scheduled timers.
   */
  @Transactional
  public int switchModuleTimers(String moduleName, boolean enable) {
    var timers = schedulerTimerRepository.findByModuleNameAndEnabledState(moduleName, enable);

    schedulerTimerRepository.switchTimersByIds(timers.stream().map(TimerDescriptorEntity::getId).toList(), enable);
    Consumer<TimerDescriptor> operation = enable ? jobSchedulingService::schedule : jobSchedulingService::delete;
    timers.forEach(
      timer -> log.info(enable ? "Scheduling timer {} {} for module {}" : "Removing timer {} {} for module {}",
        timer.getId(), timer.getType(), moduleName));
    timers.forEach(timer -> timer.getTimerDescriptor().setEnabled(true));
    timers.stream().map(TimerDescriptorEntity::getTimerDescriptor).forEach(operation);

    return timers.size();
  }

  private void validate(TimerDescriptor timerDescriptor) {
    if (timerDescriptor.getRoutingEntry().getMethods() != null
      && timerDescriptor.getRoutingEntry().getMethods().size() > 1) {
      throw new IllegalArgumentException("Only 1 method is allowed per timer");
    }

    if (isEmpty(timerDescriptor.getModuleId()) && isEmpty(timerDescriptor.getModuleName())) {
      throw new IllegalArgumentException("Module id or module name is required");
    }

    if (timerDescriptor.getType() == null) {
      throw new IllegalArgumentException("Timer type is required");
    }
  }

  private TimerDescriptor doCreate(TimerDescriptor timerDescriptor) {
    var entity = timerDescriptorMapper.convert(timerDescriptor);
    var savedEntity = schedulerTimerRepository.save(entity);
    jobSchedulingService.schedule(timerDescriptor);
    return savedEntity.getTimerDescriptor();
  }

  private TimerDescriptor doUpdate(TimerDescriptor newDescriptor) {
    var oldTimerDescriptor =
      schedulerTimerRepository.findById(newDescriptor.getId()).map(TimerDescriptorEntity::getTimerDescriptor)
        .orElseThrow(
          () -> new EntityNotFoundException("Unable to find timer descriptor with id " + newDescriptor.getId()));

    newDescriptor.modified(true);
    var convertedValue = timerDescriptorMapper.convert(newDescriptor);
    var updatedEntity = schedulerTimerRepository.save(convertedValue);
    var timerDescriptor = updatedEntity.getTimerDescriptor();
    jobSchedulingService.reschedule(oldTimerDescriptor, timerDescriptor);

    return timerDescriptor;
  }
}
