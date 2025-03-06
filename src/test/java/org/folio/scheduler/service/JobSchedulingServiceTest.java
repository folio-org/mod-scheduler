package org.folio.scheduler.service;

import static java.util.TimeZone.getTimeZone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.domain.dto.TimerUnit.DAY;
import static org.folio.scheduler.domain.dto.TimerUnit.HOUR;
import static org.folio.scheduler.domain.dto.TimerUnit.MILLISECOND;
import static org.folio.scheduler.domain.dto.TimerUnit.MINUTE;
import static org.folio.scheduler.domain.dto.TimerUnit.SECOND;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_ID;
import static org.folio.scheduler.support.TestConstants.USER_ID_UUID;
import static org.folio.scheduler.support.TestValues.timerDescriptor;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobKey.jobKey;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.TriggerKey.triggerKey;

import java.util.Date;
import java.util.stream.Stream;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.RoutingEntrySchedule;
import org.folio.scheduler.domain.dto.TimerUnit;
import org.folio.scheduler.exception.RequestValidationException;
import org.folio.scheduler.exception.TimerSchedulingException;
import org.folio.spring.FolioExecutionContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

@UnitTest
@ExtendWith(MockitoExtension.class)
class JobSchedulingServiceTest {

  @InjectMocks private JobSchedulingService service;
  @Mock private Scheduler scheduler;
  @Mock private FolioExecutionContext folioExecutionContext;

  @Captor private ArgumentCaptor<Trigger> triggerArgumentCaptor;

  @ParameterizedTest
  @MethodSource("cronBasedTimerDataProvider")
  @DisplayName("schedule_parameterized_cronScheduler")
  void schedule_parameterized_cronTrigger(String cron, String zone, String expectedCron, String expectedTimezone)
    throws SchedulerException {
    var cronSchedule = new RoutingEntrySchedule().cron(cron).zone(zone);
    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    when(folioExecutionContext.getUserId()).thenReturn(USER_ID_UUID);
    when(scheduler.scheduleJob(any(JobDetail.class), triggerArgumentCaptor.capture())).thenReturn(new Date());
    var timerDescriptor = timerDescriptor().routingEntry(new RoutingEntry().schedule(cronSchedule));

    service.schedule(timerDescriptor);

    assertThat(triggerArgumentCaptor.getValue()).isEqualTo(cronTrigger(expectedCron, expectedTimezone));
  }

  @ParameterizedTest
  @MethodSource("simpleTimerDataProvider")
  @DisplayName("schedule_parameterized_simpleTrigger")
  void schedule_parameterized_simpleTrigger(String delay, TimerUnit unit, long expectedRepeatInterval)
    throws SchedulerException {
    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    when(folioExecutionContext.getUserId()).thenReturn(USER_ID_UUID);
    when(scheduler.scheduleJob(any(JobDetail.class), triggerArgumentCaptor.capture())).thenReturn(new Date());
    var timerDescriptor = timerDescriptor().routingEntry(new RoutingEntry().delay(delay).unit(unit));

    assertThat(service.schedule(timerDescriptor)).isTrue();

    var actualTrigger = (SimpleTrigger) triggerArgumentCaptor.getValue();
    assertThat(actualTrigger).isEqualTo(simpleTrigger(expectedRepeatInterval));
    assertThat(actualTrigger.getRepeatInterval()).isEqualTo(expectedRepeatInterval);
    assertThat(actualTrigger.getRepeatCount()).isEqualTo(-1);
  }

  @Test
  void schedule_positive_timerDisabled() {
    var timerDescriptor = timerDescriptor().enabled(false);
    assertThat(service.schedule(timerDescriptor)).isFalse();
    verifyNoInteractions(scheduler);
  }

  @Test
  void schedule_negative_duplicate() throws  Exception {
    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    when(folioExecutionContext.getUserId()).thenReturn(USER_ID_UUID);
    var timerDescriptor = timerDescriptor();
    when(scheduler.scheduleJob(any(), any())).thenThrow(new ObjectAlreadyExistsException("test"));
    assertThat(service.schedule(timerDescriptor)).isFalse();
    verify(scheduler, times(1)).scheduleJob(any(), any());
  }

  @Test
  void schedule_positive_routingEntryNotDefined() {
    var timerDescriptor = timerDescriptor().routingEntry(null);
    service.schedule(timerDescriptor);
    verifyNoInteractions(scheduler);
  }

  @Test
  void schedule_positive_repeatIntervalIsZero() {
    var timerDescriptor = timerDescriptor().routingEntry(new RoutingEntry().unit(SECOND).delay("0"));
    service.schedule(timerDescriptor);
    verifyNoInteractions(scheduler);
  }

  @Test
  void schedule_negative_repeatIntervalIsLowerThanExpected() {
    var timerDescriptor = timerDescriptor().routingEntry(new RoutingEntry().unit(MILLISECOND).delay("50"));

    when(folioExecutionContext.getUserId()).thenReturn(USER_ID_UUID);

    assertThatThrownBy(() -> service.schedule(timerDescriptor))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Repeat interval must be greater than 1 second.");
    verifyNoInteractions(scheduler);
  }

  @Test
  void schedule_negative_cronAndRepeatIntervalSpecifiedAtTheSameTime() {
    var schedule = new RoutingEntrySchedule().cron("*/5 * * * * ?");
    var re = new RoutingEntry().unit(MILLISECOND).delay("50").schedule(schedule);
    var timerDescriptor = timerDescriptor().routingEntry(re);

    when(folioExecutionContext.getUserId()).thenReturn(USER_ID_UUID);

    assertThatThrownBy(() -> service.schedule(timerDescriptor))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Recurring job cannot have specified delay and schedule at the same time");
    verifyNoInteractions(scheduler);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidRoutingEntriesProvider")
  @DisplayName("schedule_parameterized_invalidRoutingEntries")
  void schedule_parameterized_invalidRoutingEntries(@SuppressWarnings("unused") String name, RoutingEntry re) {
    var timerDescriptor = timerDescriptor().routingEntry(re);

    when(folioExecutionContext.getUserId()).thenReturn(USER_ID_UUID);

    assertThatThrownBy(() -> service.schedule(timerDescriptor))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Recurring job trigger is not specified");

    verifyNoInteractions(scheduler);
  }

  @Test
  void schedule_negative_internalException() throws SchedulerException {
    var timerDescriptor = timerDescriptor().routingEntry(new RoutingEntry().unit(SECOND).delay("50"));
    when(scheduler.scheduleJob(any(JobDetail.class), any(SimpleTrigger.class)))
      .thenThrow(new SchedulerException("Failed to schedule recurring job"));
    when(folioExecutionContext.getUserId()).thenReturn(USER_ID_UUID);

    assertThatThrownBy(() -> service.schedule(timerDescriptor))
      .isInstanceOf(TimerSchedulingException.class)
      .hasMessage("Failed to schedule job");
  }

  @Test
  void reschedule_positive_routingEntryIsNull() throws SchedulerException {
    var oldTimerDesc = timerDescriptor().routingEntry(new RoutingEntry().unit(SECOND).delay("10"));
    var newTimerDesc = timerDescriptor().routingEntry(null);

    service.reschedule(oldTimerDesc, newTimerDesc);

    verify(scheduler).deleteJob(jobKey(TIMER_ID));
  }

  @MethodSource("updatedSimpleRoutingEntries")
  @ParameterizedTest(name = "[{index}] test case: {index}")
  void reschedule_parameterized_updatedSimpleTrigger(RoutingEntry re, long expectedInterval) throws SchedulerException {
    var oldTimerDesc = timerDescriptor().routingEntry(new RoutingEntry().unit(SECOND).delay("10"));
    var newTimerDesc = timerDescriptor().routingEntry(re);

    when(scheduler.rescheduleJob(eq(triggerKey(TIMER_ID)), triggerArgumentCaptor.capture())).thenReturn(new Date());

    service.reschedule(oldTimerDesc, newTimerDesc);

    var actualTrigger = (SimpleTrigger) triggerArgumentCaptor.getValue();
    assertThat(actualTrigger).isEqualTo(simpleTrigger(expectedInterval));
    assertThat(actualTrigger.getRepeatInterval()).isEqualTo(expectedInterval);
    assertThat(actualTrigger.getRepeatCount()).isEqualTo(-1);
  }

  @ParameterizedTest
  @MethodSource("updatedCronScheduleDataProvider")
  @DisplayName("reschedule_parameterized_updatedCronTrigger")
  void reschedule_parameterized_updatedCronTrigger(String cron, String zone, String expectedCron, String expectedZone)
    throws SchedulerException {
    var oldSchedule = new RoutingEntrySchedule().cron("*/5 * * * * ?").zone("PLT");
    var newSchedule = new RoutingEntrySchedule().cron(cron).zone(zone);
    var oldTimerDesc = timerDescriptor().routingEntry(new RoutingEntry().schedule(oldSchedule));
    var newTimerDesc = timerDescriptor().routingEntry(new RoutingEntry().schedule(newSchedule));

    when(scheduler.rescheduleJob(eq(triggerKey(TIMER_ID)), triggerArgumentCaptor.capture())).thenReturn(new Date());

    service.reschedule(oldTimerDesc, newTimerDesc);

    assertThat(triggerArgumentCaptor.getValue()).isEqualTo(cronTrigger(expectedCron, expectedZone));
  }

  @Test
  void reschedule_positive_routingEntryIsNullAndNotUpdated() {
    service.reschedule(timerDescriptor().routingEntry(null), timerDescriptor().routingEntry(null));
    verifyNoInteractions(scheduler);
  }

  @Test
  void reschedule_positive_routingEntryIsDisabled() {
    var oldTimerDesc = timerDescriptor().routingEntry(new RoutingEntry().unit(SECOND).delay("10")).enabled(false);
    var newTimerDesc = timerDescriptor().routingEntry(new RoutingEntry().unit(SECOND).delay("0")).enabled(false);
    service.reschedule(oldTimerDesc, newTimerDesc);
    verifyNoInteractions(scheduler);
  }

  @Test
  void reschedule_parameterized_routingEntryIsUpdatedToDisableJob() throws SchedulerException {
    var oldTimerDesc = timerDescriptor().routingEntry(new RoutingEntry().unit(SECOND).delay("10"));
    var newTimerDesc = timerDescriptor().routingEntry(new RoutingEntry().unit(SECOND).delay("0"));

    service.reschedule(oldTimerDesc, newTimerDesc);

    verify(scheduler).deleteJob(jobKey(TIMER_ID));
  }

  @Test
  void reschedule_positive_timerDescriptionEnabledTrue() throws SchedulerException {
    var oldTimerDesc = timerDescriptor().routingEntry(new RoutingEntry().unit(SECOND).delay("10"));
    var newTimerDesc = timerDescriptor().routingEntry(null);

    service.reschedule(oldTimerDesc, newTimerDesc);

    verify(scheduler).deleteJob(jobKey(TIMER_ID));
  }

  @ParameterizedTest(name = "[{index}] test case: {index}")
  @MethodSource("sameTimerRoutingEntriesProvider")
  void reschedule_positive_timerIsNotUpdated(RoutingEntry routingEntry) {
    var oldTimerDesc = timerDescriptor().routingEntry(routingEntry);
    var newTimerDesc = timerDescriptor().routingEntry(routingEntry);

    service.reschedule(oldTimerDesc, newTimerDesc);

    verifyNoInteractions(scheduler);
  }

  @Test
  void reschedule_negative_internalError() throws SchedulerException {
    var oldTimerDesc = timerDescriptor().routingEntry(new RoutingEntry().unit(SECOND).delay("10"));
    var newTimerDesc = timerDescriptor().routingEntry(new RoutingEntry().unit(SECOND).delay("25"));
    when(scheduler.rescheduleJob(eq(triggerKey(TIMER_ID)), triggerArgumentCaptor.capture())).thenThrow(
      new SchedulerException("Failed to reschedule job"));

    assertThatThrownBy(() -> service.reschedule(oldTimerDesc, newTimerDesc))
      .isInstanceOf(TimerSchedulingException.class)
      .hasMessage("Failed to reschedule job");
  }

  @Test
  void delete_positive() throws SchedulerException {
    service.delete(timerDescriptor());
    verify(scheduler).deleteJob(new JobKey(TIMER_ID));
  }

  @Test
  void delete_positive_disabled() {
    service.delete(timerDescriptor().enabled(false));
    verifyNoInteractions(scheduler);
  }

  @Test
  void delete_negative() throws SchedulerException {
    var jobKey = new JobKey(TIMER_ID);
    var timerDescriptor = timerDescriptor();
    when(scheduler.deleteJob(jobKey)).thenThrow(new SchedulerException("Failed to delete job"));
    assertThatThrownBy(() -> service.delete(timerDescriptor))
      .isInstanceOf(TimerSchedulingException.class)
      .hasMessage("Failed to delete job");
  }

  private static CronTrigger cronTrigger(String cronExpression, String timezone) {
    return newTrigger()
      .withIdentity(TIMER_ID)
      .withSchedule(cronSchedule(cronExpression).inTimeZone(getTimeZone(timezone)))
      .build();
  }

  private static SimpleTrigger simpleTrigger(long repeatInterval) {
    return newTrigger()
      .withIdentity(TIMER_ID)
      .withSchedule(simpleSchedule().repeatForever().withIntervalInMilliseconds(repeatInterval))
      .build();
  }

  public static Stream<Arguments> cronBasedTimerDataProvider() {
    return Stream.of(
      arguments("*/5 * * * *", null, "*/5 * * * * ?", "UTC"),
      arguments("*/5 * * * * ?", null, "*/5 * * * * ?", "UTC"),
      arguments("*/5 * * * * ?", "Europe/Paris", "*/5 * * * * ?", "Europe/Paris")
    );
  }

  public static Stream<Arguments> simpleTimerDataProvider() {
    return Stream.of(
      arguments("1000", MILLISECOND, 1000),
      arguments("5", MINUTE, 5 * 60 * 1000),
      arguments("25", SECOND, 25 * 1000),
      arguments("3", HOUR, 3 * 60 * 60 * 1000),
      arguments("5", DAY, 5 * 24 * 60 * 60 * 1000)
    );
  }

  public static Stream<Arguments> invalidRoutingEntriesProvider() {
    return Stream.of(
      arguments("schedule==null, delay==null, unit!=null", new RoutingEntry().delay(null).unit(DAY)),
      arguments("schedule==null, delay!=null, unit==null", new RoutingEntry().delay("100").unit(null)),
      arguments("schedule==null, delay==null, unit==null", new RoutingEntry().delay(null).unit(null))
    );
  }

  public static Stream<Arguments> sameTimerRoutingEntriesProvider() {
    return Stream.of(
      arguments(new RoutingEntry().schedule(new RoutingEntrySchedule().cron("*/5 * * * * ?"))),
      arguments(new RoutingEntry().schedule(new RoutingEntrySchedule().cron("*/5 * * * * ?").zone("ECT"))),
      arguments(new RoutingEntry().unit(SECOND).delay("10"))
    );
  }

  public static Stream<Arguments> updatedSimpleRoutingEntries() {
    return Stream.of(
      arguments(new RoutingEntry().delay("25").unit(SECOND), 25 * 1000),
      arguments(new RoutingEntry().delay("10").unit(MINUTE), 10 * 60 * 1000),
      arguments(new RoutingEntry().delay("1").unit(MINUTE), 60 * 1000)
    );
  }

  public static Stream<Arguments> updatedCronScheduleDataProvider() {
    return Stream.of(
      arguments("*/10 * * * *", null, "*/10 * * * * ?", "UTC"),
      arguments("*/10 * * * * ?", null, "*/10 * * * * ?", "UTC"),
      arguments("*/5 */2 * * *", null, "*/5 */2 * * * ?", "UTC"),
      arguments("*/5 */2 * * * ?", null, "*/5 */2 * * * ?", "UTC"),
      arguments("*/5 * * * * ?", "UTC", "*/5 * * * * ?", "UTC"),
      arguments("*/10 * * * * ?", "PLT", "*/10 * * * * ?", "PLT")
    );
  }
}
