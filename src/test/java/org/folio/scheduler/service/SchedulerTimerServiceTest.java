package org.folio.scheduler.service;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.support.TestConstants.TIMER_UUID;
import static org.folio.scheduler.support.TestValues.randomUuid;
import static org.folio.scheduler.support.TestValues.timerDescriptor;
import static org.folio.scheduler.support.TestValues.timerDescriptorEntity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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

  @InjectMocks SchedulerTimerService schedulerTimerService;
  @Mock private SchedulerTimerRepository schedulerTimerRepository;
  @Mock private TimerDescriptorMapper timerDescriptorMapper;
  @Mock private JobSchedulingService jobSchedulingService;

  @Captor private ArgumentCaptor<TimerDescriptor> timerDescriptorCaptor;

  @Test
  void findById_positive() {
    var expectedEntity = timerDescriptorEntity();
    when(schedulerTimerRepository.findById(TIMER_UUID)).thenReturn(Optional.of(expectedEntity));
    var actual = schedulerTimerService.findById(TIMER_UUID);
    assertThat(actual).isPresent().get().isEqualTo(timerDescriptor());
  }

  @Test
  void findById_positive_entityNotFound() {
    when(schedulerTimerRepository.findById(TIMER_UUID)).thenReturn(Optional.empty());
    var actual = schedulerTimerService.findById(TIMER_UUID);
    assertThat(actual).isEmpty();
  }

  @Test
  void getById_positive() {
    var expectedEntity = timerDescriptorEntity();
    when(schedulerTimerRepository.findById(TIMER_UUID)).thenReturn(Optional.of(expectedEntity));
    var actual = schedulerTimerService.getById(TIMER_UUID);
    assertThat(actual).isEqualTo(timerDescriptor());
  }

  @Test
  void getById_negative_entityNotFound() {
    var errorMessage = "Unable to find TimerDescriptor with id " + TIMER_UUID;
    when(schedulerTimerRepository.findById(TIMER_UUID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> schedulerTimerService.getById(TIMER_UUID))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage(errorMessage);
  }

  @Test
  void getAll_positive() {
    var expectedTimerDescriptors = new PageImpl<>(singletonList(timerDescriptorEntity()));
    when(schedulerTimerRepository.findAll(OffsetRequest.of(0, 100))).thenReturn(expectedTimerDescriptors);
    var actual = schedulerTimerService.getAll(0, 100);
    assertThat(actual).isEqualTo(SearchResult.of(1, singletonList(timerDescriptor())));
  }

  @Test
  void create_positive() {
    var descriptor = timerDescriptor().moduleId(MODULE_ID);
    var entity = timerDescriptorEntity(descriptor);

    when(timerDescriptorMapper.toDescriptorEntity(descriptor)).thenReturn(entity);
    when(schedulerTimerRepository.save(entity)).thenReturn(entity);
    doAnswer(inv -> true).when(jobSchedulingService).schedule(descriptor);

    var actual = schedulerTimerService.create(descriptor);
    assertThat(actual).isEqualTo(descriptor);
  }

  @Test
  void create_positive_entityIdIsNull() {
    var descriptor = timerDescriptor(null).moduleId(MODULE_ID);
    when(timerDescriptorMapper.toDescriptorEntity(timerDescriptorCaptor.capture()))
      .thenAnswer(inv -> timerDescriptorEntity(inv.getArgument(0)));
    when(schedulerTimerRepository.save(any(TimerDescriptorEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    doAnswer(inv -> true).when(jobSchedulingService).schedule(any(TimerDescriptor.class));

    var actual = schedulerTimerService.create(descriptor);

    assertThat(actual.getId()).isNotNull();
    assertThat(actual).usingRecursiveComparison().ignoringFields("id").isEqualTo(descriptor);
    assertThat(timerDescriptorCaptor.getValue().getId()).isNotNull();
  }

  @Test
  void create_negative_foundEntityById() {
    var descriptor = timerDescriptor();
    when(schedulerTimerRepository.findById(TIMER_UUID)).thenReturn(Optional.of(timerDescriptorEntity()));

    assertThatThrownBy(() -> schedulerTimerService.create(descriptor))
      .isInstanceOf(EntityExistsException.class)
      .hasMessage("TimerDescriptor already exist for id " + TIMER_UUID);
  }

  @Test
  void create_negative_moduleIdAndNameIsEmpty() {
    var descriptor = timerDescriptor().moduleId(null).moduleName(null);

    assertThatThrownBy(() -> schedulerTimerService.create(descriptor))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Module id or module name is required");
  }

  @Test
  void create_negative_timerTypeIsNull() {
    var descriptor = timerDescriptor().type(null);

    assertThatThrownBy(() -> schedulerTimerService.create(descriptor))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Timer type is required");
  }

  @Test
  void update_positive() {
    var expectedDescriptor = timerDescriptor().moduleId(MODULE_ID).modified(true);
    var existingEntity = timerDescriptorEntity();
    var entityToUpdate = timerDescriptorEntity(expectedDescriptor);

    when(schedulerTimerRepository.findById(TIMER_UUID)).thenReturn(Optional.of(existingEntity));
    when(timerDescriptorMapper.toDescriptorEntity(expectedDescriptor)).thenReturn(entityToUpdate);
    when(schedulerTimerRepository.save(entityToUpdate)).thenReturn(entityToUpdate);
    doNothing().when(jobSchedulingService).reschedule(existingEntity.getTimerDescriptor(), expectedDescriptor);

    var actual = schedulerTimerService.update(TIMER_UUID, timerDescriptor().moduleId(MODULE_ID));

    assertThat(actual).isEqualTo(expectedDescriptor);
  }

  @Test
  void update_negative_bodyWithoutId() {
    var descriptor = timerDescriptor(null);
    assertThatThrownBy(() -> schedulerTimerService.update(TIMER_UUID, descriptor))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Timer descriptor id is required");
  }

  @Test
  void update_negative_differentIdInParameterAndBody() {
    var descriptor = timerDescriptor(randomUuid());
    assertThatThrownBy(() -> schedulerTimerService.update(TIMER_UUID, descriptor))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Id in the url and in the entity must match");
  }

  @Test
  void update_negative_entityNotFound() {
    var descriptor = timerDescriptor().moduleId(MODULE_ID);
    when(schedulerTimerRepository.findById(TIMER_UUID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> schedulerTimerService.update(TIMER_UUID, descriptor))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Unable to find timer descriptor with id " + TIMER_UUID);
  }

  @Test
  void update_negative_moduleIdAndNameIsEmpty() {
    var descriptor = timerDescriptor().moduleId(null).moduleName(null);

    assertThatThrownBy(() -> schedulerTimerService.update(TIMER_UUID, descriptor))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Module id or module name is required");
  }

  @Test
  void update_negative_timerTypeIsNull() {
    var descriptor = timerDescriptor().type(null);

    assertThatThrownBy(() -> schedulerTimerService.update(TIMER_UUID, descriptor))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Timer type is required");
  }

  @Test
  void delete_positive() {
    var timerDescriptorEntity = timerDescriptorEntity();
    var timerDescriptor = timerDescriptorEntity.getTimerDescriptor();

    when(schedulerTimerRepository.findById(TIMER_UUID)).thenReturn(Optional.of(timerDescriptorEntity));
    doNothing().when(jobSchedulingService).delete(timerDescriptor);

    schedulerTimerService.delete(TIMER_UUID);

    verify(schedulerTimerRepository).findById(TIMER_UUID);
    verify(jobSchedulingService).delete(timerDescriptor);
  }

  @Test
  void delete_positive_entityNotFound() {
    schedulerTimerService.delete(TIMER_UUID);
    verify(schedulerTimerRepository).findById(TIMER_UUID);
    verifyNoInteractions(jobSchedulingService);
  }

  @Test
  void deleteAll_positive() {
    var entity = timerDescriptorEntity();
    when(schedulerTimerRepository.findAll()).thenReturn(List.of(entity));

    schedulerTimerService.deleteAll();

    verify(schedulerTimerRepository).delete(entity);
    verify(jobSchedulingService).delete(timerDescriptor());
  }

  @Test
  void create_duplicate() {
    var descriptor = timerDescriptor().moduleId(MODULE_ID);
    descriptor.setId(null);
    var entity = timerDescriptorEntity(descriptor);

    when(timerDescriptorMapper.toDescriptorEntity(descriptor)).thenReturn(entity);
    when(schedulerTimerRepository.save(entity)).thenReturn(entity);
    when(schedulerTimerRepository.findByNaturalKey(any())).thenReturn(Optional.of(entity));
    when(schedulerTimerRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

    var actual = schedulerTimerService.create(descriptor);
    descriptor.setModified(null);
    assertThat(actual).isEqualTo(descriptor);
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
      schedulerTimerRepository).findByModuleNameAndEnabledState(module, enabled);

    int result = schedulerTimerService.switchModuleTimers(module, enabled);

    assertThat(result).isEqualTo(3);

    verify(schedulerTimerRepository, times(1)).switchTimersByIds(List.of(id1, id2, id3), enabled);
  }

  TimerDescriptorEntity mockTimerDescriptorEntity(UUID id) {
    var result = new TimerDescriptorEntity();
    result.setId(id);
    result.setTimerDescriptor(new TimerDescriptor());
    return result;
  }
}
