package org.folio.scheduler.service;

import static java.util.Objects.requireNonNullElseGet;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.scheduler.utils.TimerDescriptorUtils.evalModuleName;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.configuration.properties.TimerApiConfigurationProperties;
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
  private final EntityManager entityManager;
  private final TimerApiConfigurationProperties timerApiConfigurationProperties;

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
    return getByIdInternal(uuid);
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
   * @param requestOrigin   - indicates the origin of the operation
   * @return saved {@link TimerDescriptor} object
   */
  @Transactional
  public TimerDescriptor create(TimerDescriptor timerDescriptor, RequestOrigin requestOrigin) {
    if (shouldEnforceSystemTimerProtection(requestOrigin)) {
      rejectSystemTimerMutation(timerDescriptor);
    }
    validateCreate(timerDescriptor);

    var creatingDescriptor = prepareDescriptorForCreate(timerDescriptor);

    var naturalKey = TimerDescriptorEntity.toNaturalKey(creatingDescriptor);
    return repository.findByNaturalKey(naturalKey)
      .map(existingTimer -> {
        creatingDescriptor.setId(existingTimer.getId());
        return doUpdate(creatingDescriptor);
      })
      .orElseGet(() -> doCreate(creatingDescriptor));
  }

  /**
   * Updates timer descriptor by id.
   *
   * @param uuid          - timer descriptor id.
   * @param newDescriptor - timer descriptor data to update
   * @param requestOrigin - indicates the origin of the operation
   * @return updated {@link TimerDescriptor} object
   * @throws EntityNotFoundException if timer descriptor is not found by id.
   */
  @Transactional
  public TimerDescriptor update(UUID uuid, TimerDescriptor newDescriptor, RequestOrigin requestOrigin) {
    if (shouldEnforceSystemTimerProtection(requestOrigin)) {
      rejectSystemTimerMutation(newDescriptor);
      rejectSystemTimerMutation(getByIdInternal(uuid));
    }
    validateUpdate(uuid, newDescriptor);

    var updatingDescriptor = prepareDescriptor(newDescriptor);

    return doUpdate(updatingDescriptor);
  }

  /**
   * Deletes timer descriptor by id.
   *
   * @param id            - timer descriptor id
   * @param requestOrigin - indicates the origin of the operation
   */
  @Transactional
  public void delete(UUID id, RequestOrigin requestOrigin) {
    repository.findById(id).ifPresent(entity -> {
      if (shouldEnforceSystemTimerProtection(requestOrigin)) {
        rejectSystemTimerMutation(entity.getTimerDescriptor());
      }
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

  private void validateCreate(TimerDescriptor timerDescriptor) {
    var id = timerDescriptor.getId();
    if (id != null && repository.findById(id).isPresent()) {
      throw new EntityExistsException("TimerDescriptor already exist for id " + id);
    }
    validateDescriptor(timerDescriptor);
  }

  private void validateUpdate(UUID uuid, TimerDescriptor timerDescriptor) {
    if (timerDescriptor.getId() == null) {
      throw new RequestValidationException("Timer descriptor id is required", "id", "null");
    }

    if (!Objects.equals(uuid, timerDescriptor.getId())) {
      throw new RequestValidationException("Id in the url and in the entity must match", "id", "not matched");
    }
    validateDescriptor(timerDescriptor);
  }

  private void validateDescriptor(TimerDescriptor timerDescriptor) {
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

  private TimerDescriptor prepareDescriptorForCreate(TimerDescriptor timerDescriptor) {
    return prepareDescriptor(timerDescriptor)
      .id(requireNonNullElseGet(timerDescriptor.getId(), UUID::randomUUID));
  }

  private TimerDescriptor prepareDescriptor(TimerDescriptor timerDescriptor) {
    var descriptor = mapper.deepCopy(timerDescriptor); // to avoid side effects on the input parameter
    descriptor.setModuleName(evalModuleName(descriptor));
    return descriptor;
  }

  private static void rejectSystemTimerMutation(TimerDescriptor timerDescriptor) {
    if (timerDescriptor.getType() == org.folio.scheduler.domain.dto.TimerType.SYSTEM) {
      throw new RequestValidationException(
        "SYSTEM timers are internal-only and cannot be modified via the public API", "type", "SYSTEM");
    }
  }

  private boolean shouldEnforceSystemTimerProtection(RequestOrigin requestOrigin) {
    return requestOrigin == RequestOrigin.API && !timerApiConfigurationProperties.isAllowSystemTimerMutation();
  }

  private TimerDescriptor doCreate(TimerDescriptor timerDescriptor) {
    var entity = mapper.toDescriptorEntity(timerDescriptor);
    var savedEntity = repository.saveAndFlush(entity);
    var createdDescriptor = mapper.toDescriptor(savedEntity);

    jobSchedulingService.schedule(createdDescriptor);

    return createdDescriptor;
  }

  private TimerDescriptor doUpdate(TimerDescriptor inputDescriptor) {
    assert inputDescriptor.getId() != null;
    var id = inputDescriptor.getId();

    var oldTimerDescriptor = getByIdInternal(id);

    inputDescriptor.modified(true);

    var convertedEntity = mapper.toDescriptorEntity(inputDescriptor);
    var updatedEntity = repository.saveAndFlush(convertedEntity);
    // Refresh is required to retrieve the complete audit metadata, particularly createdDate and
    // createdByUserId. Flow: mapper.toDescriptorEntity() ignores all audit fields (by design) →
    // saveAndFlush() persists the update and JPA auditing populates updatedDate/updatedByUserId
    // via @PreUpdate callback, but the created audit fields are NOT automatically retrieved since
    // they weren't part of the entity conversion → refresh() reloads the full entity from the
    // database including both created and updated audit fields → mapper.toDescriptor() can then
    // map complete audit metadata to the response. Without refresh, createdDate and createdByUserId
    // would be null/missing in the response.
    entityManager.refresh(updatedEntity);

    var updatedDescriptor = mapper.toDescriptor(updatedEntity);

    jobSchedulingService.reschedule(oldTimerDescriptor, updatedDescriptor);

    return updatedDescriptor;
  }

  private TimerDescriptor getByIdInternal(UUID id) {
    var entity = repository.findById(id).orElseThrow(
      () -> new EntityNotFoundException("Unable to find timer descriptor with id " + id));
    return mapper.toDescriptor(entity);
  }
}
