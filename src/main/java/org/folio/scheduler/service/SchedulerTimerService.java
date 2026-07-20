package org.folio.scheduler.service;

import static java.util.Objects.requireNonNullElseGet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.scheduler.utils.TimerDescriptorUtils.evalModuleName;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.configuration.properties.TimerApiConfigurationProperties;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.domain.model.SearchResult;
import org.folio.scheduler.exception.RequestValidationException;
import org.folio.scheduler.mapper.TimerDescriptorMapper;
import org.folio.scheduler.repository.SchedulerTimerRepository;
import org.folio.spring.FolioExecutionContext;
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
  private final FolioExecutionContext folioExecutionContext;

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
  public List<TimerDescriptor> findByModuleNameAndType(String moduleName,
    org.folio.scheduler.domain.model.TimerType type) {
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
  public SearchResult<TimerDescriptor> getAll(String query, Integer offset, Integer limit) {
    var offsetRequest = OffsetRequest.of(offset, limit);
    var page = isBlank(query) ? repository.findAll(offsetRequest) : repository.findByCql(query, offsetRequest);
    return SearchResult.of((int) page.getTotalElements(), page.map(mapper::toDescriptor).getContent());
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
    var timersToSwitch = enable ? schedulableTimers(timers, moduleName) : timers;

    repository.switchTimersByIds(mapItems(timersToSwitch, TimerDescriptorEntity::getId), enable);

    for (TimerDescriptorEntity timer : timersToSwitch) {
      log.info(enable
          ? "Scheduling timer: timerId = {}, timerType = {}, module = {}"
          : "Removing timer: timerId = {}, timerType = {}, module = {}",
        timer.getId(), timer.getType(), moduleName);

      var descriptor = mapper.toDescriptor(timer);
      descriptor.setEnabled(enable);

      Consumer<TimerDescriptor> operation = enable ? jobSchedulingService::schedule : jobSchedulingService::delete;
      operation.accept(descriptor);
    }

    return timersToSwitch.size();
  }

  /**
   * Returns only the timers that can be scheduled when a module is enabled, logging a warning for the rest.
   *
   * <p>A USER timer with no user id has no one to impersonate, so {@link JobSchedulingService#schedule} rejects it;
   * because scheduling joins this transaction, that failure would roll back the whole module switch. To stop a single
   * incomplete timer from aborting the entire entitlement, such timers are skipped here: they are neither enabled nor
   * scheduled and stay disabled until a user id is set (for example via an update). The user id column is empty only
   * for legacy USER timers that were disabled when the backfill migration ran; timers created or updated through the
   * API always carry a user id.</p>
   */
  private List<TimerDescriptorEntity> schedulableTimers(List<TimerDescriptorEntity> timers, String moduleName) {
    var schedulable = new ArrayList<TimerDescriptorEntity>(timers.size());
    for (var timer : timers) {
      if (isSchedulable(timer)) {
        schedulable.add(timer);
      } else {
        log.warn("Skipping USER timer without a user id on module enable [timerId: {}, module: {}]",
          timer.getId(), moduleName);
      }
    }
    return schedulable;
  }

  private static boolean isSchedulable(TimerDescriptorEntity entity) {
    return entity.getType() != org.folio.scheduler.domain.model.TimerType.USER || entity.getUserId() != null;
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
    // userId is read-only and owned by the module: it lives in a dedicated column, never in the timer_descriptor
    // jsonb, and is resolved from the timer type and execution context in doCreate/doUpdate.
    descriptor.setUserId(null);
    return descriptor;
  }

  private static void rejectSystemTimerMutation(TimerDescriptor timerDescriptor) {
    if (timerDescriptor.getType() == TimerType.SYSTEM) {
      throw new RequestValidationException(
        "SYSTEM timers are internal-only and cannot be modified via the public API", "type", "SYSTEM");
    }
  }

  // The module name is part of a timer's identity: it drives the natural key and the Quartz job/trigger group
  // (<tenant>#<moduleName>). Allowing it to change on update would orphan the existing Quartz job, so it is immutable.
  private static void rejectModuleNameChange(TimerDescriptor oldDescriptor, TimerDescriptor newDescriptor) {
    if (!Objects.equals(oldDescriptor.getModuleName(), newDescriptor.getModuleName())) {
      throw new RequestValidationException(
        "Timer module name cannot be changed", "moduleName", newDescriptor.getModuleName());
    }
  }

  private boolean shouldEnforceSystemTimerProtection(RequestOrigin requestOrigin) {
    return requestOrigin == RequestOrigin.API && !timerApiConfigurationProperties.isAllowSystemTimerMutation();
  }

  private TimerDescriptor doCreate(TimerDescriptor timerDescriptor) {
    var entity = mapper.toDescriptorEntity(timerDescriptor);
    entity.setUserId(resolveUserId(timerDescriptor.getType(), null, true));
    var savedEntity = repository.saveAndFlush(entity);
    var createdDescriptor = mapper.toDescriptor(savedEntity);

    jobSchedulingService.schedule(createdDescriptor);

    return createdDescriptor;
  }

  private TimerDescriptor doUpdate(TimerDescriptor inputDescriptor) {
    assert inputDescriptor.getId() != null;
    var id = inputDescriptor.getId();

    var oldTimerDescriptor = getByIdInternal(id);
    rejectModuleNameChange(oldTimerDescriptor, inputDescriptor);

    inputDescriptor.modified(true);

    var convertedEntity = mapper.toDescriptorEntity(inputDescriptor);
    convertedEntity.setUserId(resolveUserId(inputDescriptor.getType(), oldTimerDescriptor.getUserId(),
      timerApiConfigurationProperties.isAllowUserIdUpdate()));

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

  /**
   * Resolves the userId to persist for a timer based on its type.
   *
   * <p>
   * SYSTEM timers are not owned by a user, so {@code null} is returned - their user is resolved from the tenant's
   * system user at execution time. USER timers keep their {@code existingUserId} unless a refresh is requested or none
   * has been assigned yet, in which case the userId is taken from the current execution context.
   * </p>
   *
   * @param type - timer type that determines how the userId is resolved
   * @param existingUserId - userId currently stored for the timer, may be {@code null}
   * @param allowRefresh - whether an existing userId may be replaced with the one from the current context
   * @return the userId to persist, or {@code null} for SYSTEM timers
   */
  private UUID resolveUserId(TimerType type, UUID existingUserId, boolean allowRefresh) {
    return switch (type) {
      case USER -> (allowRefresh || existingUserId == null)
        ? getUserIdFromContext()
        : existingUserId;
      case SYSTEM -> null;
    };
  }

  private UUID getUserIdFromContext() {
    var contextUserId = folioExecutionContext.getUserId();
    if (contextUserId == null) {
      throw new RequestValidationException("User timer requires a userId");
    }
    return contextUserId;
  }

  private TimerDescriptor getByIdInternal(UUID id) {
    var entity = repository.findById(id).orElseThrow(
      () -> new EntityNotFoundException("Unable to find timer descriptor with id " + id));
    return mapper.toDescriptor(entity);
  }
}
