package org.folio.scheduler.controller;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerDescriptorList;
import org.folio.scheduler.rest.resource.SchedulerApi;
import org.folio.scheduler.service.SchedulerTimerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SchedulerTimerController implements SchedulerApi {

  private final SchedulerTimerService schedulerTimerService;

  @Override
  public ResponseEntity<TimerDescriptor> createSchedulerTimers(TimerDescriptor timerDescriptor) {
    return ResponseEntity.status(CREATED).body(schedulerTimerService.create(timerDescriptor));
  }

  @Override
  public ResponseEntity<Void> deleteSchedulerTimerById(UUID id) {
    schedulerTimerService.delete(id);
    return ResponseEntity.status(NO_CONTENT).build();
  }

  @Override
  public ResponseEntity<TimerDescriptor> getSchedulerTimerById(UUID id) {
    return ResponseEntity.ok(schedulerTimerService.getById(id));
  }

  @Override
  public ResponseEntity<TimerDescriptorList> getSchedulerTimers(Integer offset, Integer limit) {
    var result = schedulerTimerService.getAll(offset, limit);
    return ResponseEntity.ok(new TimerDescriptorList()
      .timerDescriptors(result.getRecords())
      .totalRecords(result.getTotalRecords()));
  }

  @Override
  public ResponseEntity<TimerDescriptor> updateSchedulerTimerById(UUID id, TimerDescriptor timerDescriptor) {
    return ResponseEntity.ok(schedulerTimerService.update(id, timerDescriptor));
  }
}
