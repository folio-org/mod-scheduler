package org.folio.scheduler.integration.kafka.model;

import java.util.List;
import lombok.Data;
import org.folio.scheduler.domain.dto.RoutingEntry;

@Data
public class ScheduledTimers {

  /**
   * Module identifier.
   */
  private String moduleId;

  /**
   * Application identifier.
   */
  private String applicationId;

  /**
   * List with defined folio resources and corresponding permissions.
   */
  private List<RoutingEntry> timers;

  /**
   * Sets moduleId for {@link ScheduledTimers} and returns {@link ScheduledTimers}.
   *
   * @return this {@link ScheduledTimers} with new moduleId value
   */
  public ScheduledTimers moduleId(String moduleId) {
    this.moduleId = moduleId;
    return this;
  }

  /**
   * Sets applicationId for {@link ScheduledTimers} and returns {@link ScheduledTimers}.
   *
   * @return this {@link ScheduledTimers} with new applicationId value
   */
  public ScheduledTimers applicationId(String applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  /**
   * Sets timers for {@link ScheduledTimers} and returns {@link ScheduledTimers}.
   *
   * @return this {@link ScheduledTimers} with new timers value
   */
  public ScheduledTimers timers(List<RoutingEntry> timers) {
    this.timers = timers;
    return this;
  }
}
