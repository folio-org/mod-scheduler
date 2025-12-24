package org.folio.scheduler.integration.kafka;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.scheduler.domain.model.TimerType.SYSTEM;
import static org.folio.scheduler.utils.OkapiRequestUtils.getStaticPath;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.utils.SemverUtils;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.integration.kafka.model.EntitlementEvent;
import org.folio.scheduler.integration.kafka.model.ResourceEvent;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class KafkaEventService {

  private final FolioModuleMetadata folioModuleMetadata;
  private final SchedulerTimerService schedulerTimerService;

  public void createTimers(ResourceEvent event) {
    var timers = event.getNewValue();
    var tenant = event.getTenant();

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeaders(tenant))) {
      var moduleId = timers.getModuleId();
      var moduleName = SemverUtils.getName(moduleId);
      var routingEntries = timers.getTimers();

      logCreatingTimers(routingEntries);
      for (var re : routingEntries) {
        schedulerTimerService.create(createTimerDescriptor(re, moduleName, moduleId));
      }
    }
  }

  public void updateTimers(ResourceEvent event) {
    var newTimers = event.getNewValue();
    var tenant = event.getTenant();

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeaders(tenant))) {
      var moduleId = newTimers.getModuleId();
      var moduleName = SemverUtils.getName(moduleId);

      var timers = schedulerTimerService.findByModuleNameAndType(moduleName, SYSTEM);
      if (isNotEmpty(timers)) {
        logDeletingTimers(timers);
        for (var timer : timers) {
          schedulerTimerService.delete(timer.getId());
        }
      }

      logCreatingTimers(newTimers.getTimers());
      for (var routingEntry : newTimers.getTimers()) {
        schedulerTimerService.create(createTimerDescriptor(routingEntry, moduleName, moduleId));
      }
    }
  }

  public void deleteTimers(ResourceEvent event) {
    var tenant = event.getTenant();

    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeaders(tenant))) {
      var moduleName = SemverUtils.getName(event.getOldValue().getModuleId());
      
      var timers = schedulerTimerService.findByModuleNameAndType(moduleName, SYSTEM);
      if (isEmpty(timers)) {
        return;
      }

      logDeletingTimers(timers);
      for (var timer : timers) {
        schedulerTimerService.delete(timer.getId());
      }
    }
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

  private void switchTimers(String moduleId, String tenantName, boolean enable) {
    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, prepareContextHeaders(tenantName))) {
      var moduleName = SemverUtils.getName(moduleId);
      int switched = schedulerTimerService.switchModuleTimers(moduleName, enable);
      log.info("{} timers were switched to enabled={} state for module {} and tenant {}",
        switched, enable, moduleName, tenantName);
    }
  }

  private static Map<String, Collection<String>> prepareContextHeaders(String tenant) {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(TENANT, singletonList(tenant));
    return headers;
  }

  private static TimerDescriptor createTimerDescriptor(RoutingEntry routingEntry, String moduleName, String moduleId) {
    return new TimerDescriptor().enabled(TRUE)
      .type(TimerType.SYSTEM)
      .moduleName(moduleName)
      .moduleId(moduleId)
      .routingEntry(routingEntry);
  }

  private static void logCreatingTimers(List<RoutingEntry> entries) {
    log.debug("Processing scheduled job event from kafka: timers = {}",
      () -> mapItems(entries, KafkaEventService::getRoutingEntryKey));
  }

  private static void logDeletingTimers(List<TimerDescriptor> timers) {
    log.debug("Deleting timers: timers = {}",
      () -> mapItems(timers, t -> getRoutingEntryKey(t.getRoutingEntry())));
  }

  private static String getRoutingEntryKey(RoutingEntry routingEntry) {
    var methods = String.join("|", routingEntry.getMethods());
    return methods + " " + getStaticPath(routingEntry);
  }
}
