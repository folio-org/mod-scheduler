package org.folio.scheduler.integration.kafka;

import static java.lang.Boolean.TRUE;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.scheduler.domain.model.TimerType.SYSTEM;
import static org.folio.scheduler.utils.OkapiRequestUtils.getStaticPath;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.utils.SemverUtils;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.integration.kafka.model.EntitlementEvent;
import org.folio.scheduler.integration.kafka.model.ResourceEvent;
import org.folio.scheduler.service.SchedulerTimerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class KafkaEventService {

  private final SchedulerTimerService schedulerTimerService;
  private final TimerTableCheckService timerTableCheckService;

  public void createTimers(ResourceEvent event) {
    var newTimers = event.getNewValue();

    var moduleId = newTimers.getModuleId();
    var moduleName = SemverUtils.getName(moduleId);

    createModuleSystemTimers(newTimers.getTimers(), moduleName, moduleId);
  }

  public void updateTimers(ResourceEvent event) {
    var newTimers = event.getNewValue();

    var moduleId = newTimers.getModuleId();
    var moduleName = SemverUtils.getName(moduleId);

    deleteModuleSystemTimers(moduleName);

    createModuleSystemTimers(newTimers.getTimers(), moduleName, moduleId);
  }

  public void deleteTimers(ResourceEvent event) {
    var tenant = event.getTenant();
    var moduleName = SemverUtils.getName(event.getOldValue().getModuleId());

    if (!timerTableCheckService.tableExists()) {
      log.debug("Cannot delete system timers for given module and tenant because the timer table is missing: "
        + "module = {}, tenant = {}. Operation is ignored.", moduleName, tenant);
      return;
    }

    deleteModuleSystemTimers(moduleName);
  }

  public void enableTimers(EntitlementEvent event) {
    var moduleId = event.getModuleId();
    var tenant = event.getTenantName();
    switchTimers(moduleId, tenant, true);
  }

  public void disableTimers(EntitlementEvent event) {
    var moduleId = event.getModuleId();
    var tenant = event.getTenantName();
    switchTimers(moduleId, tenant, false);
  }

  private void createModuleSystemTimers(List<RoutingEntry> routingEntries, String moduleName, String moduleId) {
    log.info("Creating system timers: moduleId = {}, timers = {}",
      () -> moduleId, () -> mapItems(routingEntries, KafkaEventService::getRoutingEntryKey));

    for (var re : routingEntries) {
      schedulerTimerService.create(createTimerDescriptor(re, moduleName, moduleId));
    }
  }

  private void deleteModuleSystemTimers(String moduleName) {
    var timers = schedulerTimerService.findByModuleNameAndType(moduleName, SYSTEM);
    if (isEmpty(timers)) {
      return;
    }

    log.info("Deleting system timers: moduleName = {}, timers = {}",
      () -> moduleName,
      () -> mapItems(timers, t -> String.join("@", String.valueOf(t.getId()), getRoutingEntryKey(t.getRoutingEntry())))
    );

    for (var timer : timers) {
      schedulerTimerService.delete(timer.getId());
    }
  }

  private void switchTimers(String moduleId, String tenantName, boolean enable) {
    var moduleName = SemverUtils.getName(moduleId);

    if (!timerTableCheckService.tableExists()) {
      log.debug("Cannot switch timers for given module and tenant because the timer table is missing: "
        + "module = {}, tenant = {}. Operation is ignored.", moduleName, tenantName);
      return;
    }

    int switched = schedulerTimerService.switchModuleTimers(moduleName, enable);

    log.info("Timers were switched to new state: count = {}, state = {}, module = {}, tenant = {}",
      switched, enable ? "enable" : "disable", moduleName, tenantName);
  }

  private static TimerDescriptor createTimerDescriptor(RoutingEntry routingEntry, String moduleName, String moduleId) {
    return new TimerDescriptor().enabled(TRUE)
      .type(TimerType.SYSTEM)
      .moduleName(moduleName)
      .moduleId(moduleId)
      .routingEntry(routingEntry);
  }

  private static String getRoutingEntryKey(RoutingEntry routingEntry) {
    var methods = String.join("|", routingEntry.getMethods());
    return methods + " " + getStaticPath(routingEntry);
  }
}
