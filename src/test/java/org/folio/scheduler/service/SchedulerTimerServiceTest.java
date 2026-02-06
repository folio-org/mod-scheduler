package org.folio.scheduler.service;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.support.TestConstants.TIMER_UUID;
import static org.folio.scheduler.support.TestValues.randomUuid;
import static org.folio.scheduler.support.TestValues.timerDescriptor;
import static org.folio.scheduler.support.TestValues.timerDescriptorEntity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.domain.model.SearchResult;
import org.folio.scheduler.exception.RequestValidationException;
import org.folio.scheduler.mapper.TimerDescriptorMapper;
import org.folio.scheduler.repository.SchedulerTimerRepository;
import org.folio.spring.data.OffsetRequest;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SchedulerTimerServiceTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";

  @InjectMocks SchedulerTimerService service;
  @Mock private SchedulerTimerRepository repository;
  @Mock private TimerDescriptorMapper mapper;
  @Mock private JobSchedulingService jobSchedulingService;

  @Captor private ArgumentCaptor<TimerDescriptor> timerDescriptorCaptor;

  @Test
  void findById_positive() {
    var expectedEntity = timerDescriptorEntity();
    when(repository.findById(TIMER_UUID)).thenReturn(Optional.of(expectedEntity));
    when(mapper.toDescriptor(expectedEntity)).thenReturn(timerDescriptor());

    var actual = service.findById(TIMER_UUID);

    assertThat(actual).isPresent().get().isEqualTo(timerDescriptor());
  }

  @Test
  void findById_positive_entityNotFound() {
    when(repository.findById(TIMER_UUID)).thenReturn(Optional.empty());
    var actual = service.findById(TIMER_UUID);
    assertThat(actual).isEmpty();
  }

  @Test
  void getById_positive() {
    var expectedEntity = timerDescriptorEntity();
    when(repository.findById(TIMER_UUID)).thenReturn(Optional.of(expectedEntity));
    when(mapper.toDescriptor(expectedEntity)).thenReturn(timerDescriptor());
    var actual = service.getById(TIMER_UUID);
    assertThat(actual).isEqualTo(timerDescriptor());
  }

  @Test
  void getById_negative_entityNotFound() {
    var errorMessage = "Unable to find TimerDescriptor with id " + TIMER_UUID;
    when(repository.findById(TIMER_UUID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(TIMER_UUID))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage(errorMessage);
  }

  @Test
  void getAll_positive() {
    var entity = timerDescriptorEntity();
    var expectedTimerDescriptors = new PageImpl<>(singletonList(entity));
    when(repository.findAll(OffsetRequest.of(0, 100))).thenReturn(expectedTimerDescriptors);
    when(mapper.toDescriptor(entity)).thenReturn(timerDescriptor());
    var actual = service.getAll(0, 100);
    assertThat(actual).isEqualTo(SearchResult.of(1, singletonList(timerDescriptor())));
  }

  @Test
  void create_positive() {
    var descriptor = timerDescriptor().moduleId(MODULE_ID);
    var descriptorCopy = timerDescriptor().moduleId(MODULE_ID);
    var entity = timerDescriptorEntity(descriptorCopy);

    when(mapper.deepCopy(descriptor)).thenReturn(descriptorCopy);
    when(repository.findByNaturalKey(TimerDescriptorEntity.toNaturalKey(descriptorCopy))).thenReturn(Optional.empty());

    when(mapper.toDescriptorEntity(descriptorCopy)).thenReturn(entity);
    when(repository.save(entity)).thenReturn(entity);
    when(mapper.toDescriptor(entity)).thenReturn(descriptorCopy);
    when(jobSchedulingService.schedule(descriptorCopy)).thenReturn(true);

    var actual = service.create(descriptor);
    assertThat(actual).isEqualTo(descriptorCopy);
  }

  @Test
  void create_positive_entityIdIsNull() {
    var descriptor = timerDescriptor(null).moduleId(MODULE_ID);
    var descriptorCopy = timerDescriptor(null).moduleId(MODULE_ID);

    when(mapper.deepCopy(descriptor)).thenReturn(descriptorCopy);
    when(repository.findByNaturalKey(TimerDescriptorEntity.toNaturalKey(descriptorCopy))).thenReturn(Optional.empty());

    when(mapper.toDescriptorEntity(timerDescriptorCaptor.capture()))
      .thenAnswer(inv -> timerDescriptorEntity(inv.getArgument(0)));
    when(repository.save(any(TimerDescriptorEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(mapper.toDescriptor(any(TimerDescriptorEntity.class)))
      .thenAnswer(inv -> ((TimerDescriptorEntity) inv.getArgument(0)).getTimerDescriptor());
    when(jobSchedulingService.schedule(any(TimerDescriptor.class))).thenReturn(true);

    var actual = service.create(descriptor);

    assertThat(actual.getId()).isNotNull();
    assertThat(actual).usingRecursiveComparison().ignoringFields("id").isEqualTo(descriptor);
    assertThat(timerDescriptorCaptor.getValue().getId()).isNotNull();
  }

  @Test
  void create_negative_foundEntityById() {
    var descriptor = timerDescriptor();
    when(repository.findById(TIMER_UUID)).thenReturn(Optional.of(timerDescriptorEntity()));

    assertThatThrownBy(() -> service.create(descriptor))
      .isInstanceOf(EntityExistsException.class)
      .hasMessage("TimerDescriptor already exist for id " + TIMER_UUID);
  }

  @Test
  void create_negative_moduleIdAndNameIsEmpty() {
    var descriptor = timerDescriptor().moduleId(null).moduleName(null);

    assertThatThrownBy(() -> service.create(descriptor))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Module id or module name is required");
  }

  @Test
  void create_negative_timerTypeIsNull() {
    var descriptor = timerDescriptor().type(null);

    assertThatThrownBy(() -> service.create(descriptor))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Timer type is required");
  }

  @Test
  void update_positive() {
    var inputDescriptor = timerDescriptorWithPath("/modified");
    var inputDescriptorCopy = timerDescriptorWithPath("/modified");

    var existingDescriptor = timerDescriptor().moduleId(MODULE_ID).modified(null);
    var existingEntity = timerDescriptorEntity(existingDescriptor);

    var expectedDescriptor = timerDescriptorWithPath("/modified").modified(true);
    var entityToUpdate = timerDescriptorEntity(expectedDescriptor);

    when(mapper.deepCopy(inputDescriptor)).thenReturn(inputDescriptorCopy);

    when(repository.findById(TIMER_UUID)).thenReturn(Optional.of(existingEntity));
    // Use thenAnswer to return based on actual entity content to avoid entity equality issues
    // because entity equality is based on id only and in the test there is new entity instances with the same id
    // but different content
    when(mapper.toDescriptor(any(TimerDescriptorEntity.class))).thenAnswer(inv -> {
      var entity = (TimerDescriptorEntity) inv.getArgument(0);

      if (entity.getTimerDescriptor().getModified() == null) {
        return existingDescriptor;
      }

      return expectedDescriptor;
    });

    // Use any() because service calls this with the deep copy (inputDescriptorCopy), not expectedDescriptor
    when(mapper.toDescriptorEntity(any(TimerDescriptor.class))).thenReturn(entityToUpdate);

    when(repository.save(entityToUpdate)).thenReturn(entityToUpdate);
    doNothing().when(jobSchedulingService).reschedule(existingDescriptor, expectedDescriptor);

    var actual = service.update(TIMER_UUID, inputDescriptor);

    assertThat(actual).isEqualTo(expectedDescriptor);
  }

  @Test
  void update_negative_bodyWithoutId() {
    var descriptor = timerDescriptor(null);
    assertThatThrownBy(() -> service.update(TIMER_UUID, descriptor))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Timer descriptor id is required");
  }

  @Test
  void update_negative_differentIdInParameterAndBody() {
    var descriptor = timerDescriptor(randomUuid());
    assertThatThrownBy(() -> service.update(TIMER_UUID, descriptor))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Id in the url and in the entity must match");
  }

  @Test
  void update_negative_entityNotFound() {
    var descriptor = timerDescriptor().moduleId(MODULE_ID);
    when(mapper.deepCopy(descriptor)).thenReturn(descriptor);
    when(repository.findById(TIMER_UUID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(TIMER_UUID, descriptor))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Unable to find timer descriptor with id " + TIMER_UUID);
  }

  @Test
  void update_negative_moduleIdAndNameIsEmpty() {
    var descriptor = timerDescriptor().moduleId(null).moduleName(null);

    assertThatThrownBy(() -> service.update(TIMER_UUID, descriptor))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Module id or module name is required");
  }

  @Test
  void update_negative_timerTypeIsNull() {
    var descriptor = timerDescriptor().type(null);

    assertThatThrownBy(() -> service.update(TIMER_UUID, descriptor))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Timer type is required");
  }

  @Test
  void delete_positive() {
    var timerDescriptorEntity = timerDescriptorEntity();
    var timerDescriptor = timerDescriptorEntity.getTimerDescriptor();

    when(repository.findById(TIMER_UUID)).thenReturn(Optional.of(timerDescriptorEntity));
    doNothing().when(jobSchedulingService).delete(timerDescriptor);

    service.delete(TIMER_UUID);

    verify(repository).findById(TIMER_UUID);
    verify(jobSchedulingService).delete(timerDescriptor);
  }

  @Test
  void delete_positive_entityNotFound() {
    service.delete(TIMER_UUID);
    verify(repository).findById(TIMER_UUID);
    verifyNoInteractions(jobSchedulingService);
  }

  @Test
  void deleteAll_positive() {
    var entity = timerDescriptorEntity();
    when(repository.findAll()).thenReturn(List.of(entity));

    service.deleteAll();

    verify(repository).delete(entity);
    verify(jobSchedulingService).delete(timerDescriptor());
  }

  @Test
  void create_duplicate() {
    var descriptor = timerDescriptor().moduleId(MODULE_ID).id(null);
    var descriptorCopy = timerDescriptor().moduleId(MODULE_ID).id(null);
    var entity = timerDescriptorEntity(descriptorCopy);
    var existingDescriptor = timerDescriptor().moduleId(MODULE_ID);
    var updatedDescriptor = timerDescriptor().moduleId(MODULE_ID).modified(true);

    when(mapper.deepCopy(descriptor)).thenReturn(descriptorCopy);
    when(mapper.toDescriptorEntity(any(TimerDescriptor.class))).thenReturn(entity);
    when(repository.save(entity)).thenReturn(entity);
    when(repository.findByNaturalKey(any())).thenReturn(Optional.of(entity));
    when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));
    when(mapper.toDescriptor(entity)).thenReturn(existingDescriptor, updatedDescriptor);
    doNothing().when(jobSchedulingService).reschedule(any(TimerDescriptor.class), any(TimerDescriptor.class));

    var actual = service.create(descriptor);
    assertThat(actual).isEqualTo(updatedDescriptor);
  }

  @Test
  void switchModuleTimers_callsRepo() {
    var module = "mod-foo";
    var enabled = true;

    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();
    var id3 = UUID.randomUUID();

    doReturn(
      List.of(mockTimerDescriptorEntity(id1), mockTimerDescriptorEntity(id2), mockTimerDescriptorEntity(id3))).when(
      repository).findByModuleNameAndEnabledState(module, enabled);

    int result = service.switchModuleTimers(module, enabled);

    assertThat(result).isEqualTo(3);

    verify(repository, times(1)).switchTimersByIds(List.of(id1, id2, id3), enabled);
  }

  private static TimerDescriptorEntity mockTimerDescriptorEntity(UUID id) {
    var result = new TimerDescriptorEntity();
    result.setId(id);
    result.setTimerDescriptor(new TimerDescriptor());
    return result;
  }

  private static TimerDescriptor timerDescriptorWithPath(String path) {
    var inputDescriptor = timerDescriptor().moduleId(MODULE_ID);
    inputDescriptor.getRoutingEntry().setPathPattern(path);
    return inputDescriptor;
  }
}
