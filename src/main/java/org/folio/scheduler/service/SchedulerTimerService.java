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

  private final TimerDescriptorMapper mapper;
  private final JobSchedulingService jobSchedulingService;
  private final SchedulerTimerRepository repository;

  /**
   * Returns {@link Optional} of {@link TimerDescriptor} object by id.
   *
   * @param uuid - timer descriptor id as {@link UUID} object
   * @return found {@link TimerDescriptor} object in {@link Optional} wrapper, it will be empty if value is not found.
   */
  @Transactional(readOnly = true)
  public Optional<TimerDescriptor> findById(UUID uuid) {
    return repository.findById(uuid).map(mapper::toDescriptor);
  }

  @Transactional(readOnly = true)
  public List<TimerDescriptor> findByModuleNameAndType(String moduleName, TimerType type) {
    return mapItems(repository.findByModuleNameAndType(moduleName, type), mapper::toDescriptor);
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
    return repository.findById(uuid).map(mapper::toDescriptor).orElseThrow(
      () -> new EntityNotFoundException("Unable to find TimerDescriptor with id " + uuid));
  }

  /**
   * Retrieves all timer descriptors.
   *
   * @return saved {@link TimerDescriptor} object
   */
  @Transactional(readOnly = true)
  public SearchResult<TimerDescriptor> getAll(Integer offset, Integer limit) {
    return SearchResult.of(repository.findAll(OffsetRequest.of(offset, limit)).stream()
      .map(mapper::toDescriptor).toList());
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
      var entityById = repository.findById(id);
      if (entityById.isPresent()) {
        throw new EntityExistsException("TimerDescriptor already exist for id " + id);
      }
    }

    validate(timerDescriptor);

    timerDescriptor.setId(defaultIfNull(id, UUID.randomUUID()));
    timerDescriptor.setModuleName(evalModuleName(timerDescriptor));

    var naturalKey = TimerDescriptorEntity.toNaturalKey(timerDescriptor);
    return repository.findByNaturalKey(naturalKey)
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
    repository.findById(id).ifPresent(entity -> {
      repository.delete(entity);
      jobSchedulingService.delete(entity.getTimerDescriptor());
    });
  }

  /**
   * Deletes all scheduled timers, assigned to tenant.
   */
  @Transactional
  public void deleteAll() {
    var allEntities = repository.findAll();
    for (var timerDescriptorEntity : allEntities) {
      repository.delete(timerDescriptorEntity);
      jobSchedulingService.delete(timerDescriptorEntity.getTimerDescriptor());
    }
  }

  /**
   * Switch module's scheduled timers.
   */
  @Transactional
  public int switchModuleTimers(String moduleName, boolean enable) {
    var timers = repository.findByModuleNameAndEnabledState(moduleName, enable);

    repository.switchTimersByIds(mapItems(timers, TimerDescriptorEntity::getId), enable);

    for (TimerDescriptorEntity timer : timers) {
      log.info(enable
          ? "Scheduling timer: timerId = {}, timerType = {}, module = {}"
          : "Removing timer: timerId = {}, timerType = {}, module = {}",
        timer.getId(), timer.getType(), moduleName);

      var descriptor = timer.getTimerDescriptor();
      descriptor.setEnabled(enable);

      Consumer<TimerDescriptor> operation = enable ? jobSchedulingService::schedule : jobSchedulingService::delete;
      operation.accept(descriptor);
    }

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
    var entity = mapper.toDescriptorEntity(timerDescriptor);
    var savedEntity = repository.save(entity);
    var createdDescriptor = mapper.toDescriptor(savedEntity);

    jobSchedulingService.schedule(createdDescriptor);

    return createdDescriptor;
  }

  private TimerDescriptor doUpdate(TimerDescriptor newDescriptor) {
    assert newDescriptor.getId() != null;

    var oldTimerDescriptor = repository.findById(newDescriptor.getId())
      .map(mapper::toDescriptor)
      .orElseThrow(
        () -> new EntityNotFoundException("Unable to find timer descriptor with id " + newDescriptor.getId()));

    newDescriptor.modified(true);
    var convertedValue = mapper.toDescriptorEntity(newDescriptor);
    var updatedEntity = repository.save(convertedValue);
    var updatedDescriptor = mapper.toDescriptor(updatedEntity);
    jobSchedulingService.reschedule(oldTimerDescriptor, updatedDescriptor);

    return updatedDescriptor;
  }
}
