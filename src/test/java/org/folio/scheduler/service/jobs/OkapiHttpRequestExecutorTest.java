package org.folio.scheduler.service.jobs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_UUID;
import static org.folio.scheduler.support.TestConstants.USER_ID;
import static org.folio.scheduler.support.TestConstants.USER_ID_UUID;
import static org.folio.scheduler.support.TestConstants.USER_TOKEN;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.folio.scheduler.configuration.properties.OkapiConfigurationProperties;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.integration.OkapiClient;
import org.folio.scheduler.integration.keycloak.SystemUserService;
import org.folio.scheduler.service.ScheduledJobDetail;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.scheduler.service.UserImpersonationService;
import org.folio.scheduler.support.TestValues;
import org.folio.spring.FolioModuleMetadata;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiHttpRequestExecutorTest {

  private static final String TEST_MODULE_ID = "mod-test-1.0";
  private static final String TEST_MODULE_NAME = "mod-test";
  private static final String OKAPI_URL = "http://okapi:9130";
  private static final String MODULE_NAME = "mod-scheduler";
  private static final String SYSTEM_USER_ID = "99999999-9999-9999-9999-999999999999";

  @InjectMocks private OkapiHttpRequestExecutor job;
  @Mock private OkapiClient okapiClient;
  @Mock private JobExecutionContext jobExecutionContext;
  @Mock private FolioModuleMetadata folioModuleMetadata;
  @Mock private SchedulerTimerService schedulerTimerService;
  @Mock private OkapiConfigurationProperties okapiConfigurationProperties;
  @Mock private UserImpersonationService userImpersonationService;
  @Mock private SystemUserService systemUserService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(okapiClient, jobExecutionContext, schedulerTimerService, okapiConfigurationProperties);
  }

  @Test
  void execute_positive_systemTimerIgnoresStoredUserId() {
    var re = new RoutingEntry().methods(List.of("GET")).pathPattern("/test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(jobExecutionContext.getJobDetail()).thenReturn(systemJobDetailWithStaleUserId());
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(userImpersonationService.impersonate(TENANT_ID, SYSTEM_USER_ID)).thenReturn(USER_TOKEN);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(schedulerTimerService.getById(TIMER_UUID)).thenReturn(systemTimerDescriptor(re));

    job.execute(jobExecutionContext);

    verify(systemUserService).findSystemUserId(TENANT_ID);
    verify(userImpersonationService).impersonate(TENANT_ID, SYSTEM_USER_ID);
    verify(okapiClient).doGet(fromUriString("http://test-endpoint").build().toUri(), TEST_MODULE_ID);
  }

  @Test
  void execute_positive_systemTimerWithoutUserIdUsesSystemUser() {
    var re = new RoutingEntry().methods(List.of("GET")).pathPattern("/test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(jobExecutionContext.getJobDetail()).thenReturn(systemJobDetail());
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(userImpersonationService.impersonate(TENANT_ID, SYSTEM_USER_ID)).thenReturn(USER_TOKEN);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(schedulerTimerService.getById(TIMER_UUID)).thenReturn(systemTimerDescriptor(re));

    job.execute(jobExecutionContext);

    verify(systemUserService).findSystemUserId(TENANT_ID);
    verify(okapiClient).doGet(fromUriString("http://test-endpoint").build().toUri(), TEST_MODULE_ID);
  }

  @Test
  void execute_positive_userTimerUsesStoredUserId() {
    var re = new RoutingEntry().methods(List.of("GET")).pathPattern("/test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(jobExecutionContext.getJobDetail()).thenReturn(userJobDetail());
    when(userImpersonationService.impersonate(TENANT_ID, USER_ID)).thenReturn(USER_TOKEN);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(schedulerTimerService.getById(TIMER_UUID)).thenReturn(userTimerDescriptor(re));

    job.execute(jobExecutionContext);

    verify(userImpersonationService).impersonate(TENANT_ID, USER_ID);
    verify(okapiClient).doGet(fromUriString("http://test-endpoint").build().toUri(), TEST_MODULE_NAME);
    verifyNoInteractions(systemUserService);
  }

  @Test
  void execute_negative_userTimerWithoutUserId() {
    when(jobExecutionContext.getJobDetail()).thenReturn(userJobDetailWithoutUserId());

    assertThatThrownBy(() -> job.execute(jobExecutionContext))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Failed to prepare timer request: userId is null for user timer [tenant: " + TENANT_ID + "]");

    verifyNoInteractions(okapiClient, schedulerTimerService, systemUserService, userImpersonationService);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "null", " NULL "})
  void execute_negative_userTokenIsBlank(String userToken) {
    when(jobExecutionContext.getJobDetail()).thenReturn(systemJobDetail());
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(userImpersonationService.impersonate(TENANT_ID, SYSTEM_USER_ID)).thenReturn(userToken);

    assertThatThrownBy(() -> job.execute(jobExecutionContext))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Failed to prepare timer request: user impersonation token is blank [tenant: test, userId: "
        + SYSTEM_USER_ID + "]");

    verifyNoInteractions(okapiClient, schedulerTimerService);
  }

  @Test
  void execute_positive_methodNotDefined() {
    var re = new RoutingEntry().path("test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(systemJobDetail());
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(userImpersonationService.impersonate(TENANT_ID, SYSTEM_USER_ID)).thenReturn(USER_TOKEN);
    when(schedulerTimerService.getById(TIMER_UUID)).thenReturn(systemTimerDescriptor(re));

    job.execute(jobExecutionContext);

    verify(okapiClient).doPost(fromUriString("http://test-endpoint").build().toUri(), TEST_MODULE_ID);
  }

  @Test
  void execute_positive_moduleNameAsHint() {
    var re = new RoutingEntry().methods(List.of("GET")).pathPattern("/test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(jobExecutionContext.getJobDetail()).thenReturn(systemJobDetail());
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(userImpersonationService.impersonate(TENANT_ID, SYSTEM_USER_ID)).thenReturn(USER_TOKEN);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(schedulerTimerService.getById(TIMER_UUID)).thenReturn(
      TestValues.timerDescriptor().type(TimerType.SYSTEM).routingEntry(re).moduleName(TEST_MODULE_NAME));

    job.execute(jobExecutionContext);

    verify(okapiClient).doGet(fromUriString("http://test-endpoint").build().toUri(), TEST_MODULE_NAME);
  }

  @Test
  void execute_negative_httpException() {
    var re = new RoutingEntry().path("test-endpoint").methods(List.of("DELETE"));
    var expectedUri = fromUriString("http://test-endpoint").build().toUri();
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(systemJobDetail());
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(userImpersonationService.impersonate(TENANT_ID, SYSTEM_USER_ID)).thenReturn(USER_TOKEN);
    when(schedulerTimerService.getById(TIMER_UUID)).thenReturn(systemTimerDescriptor(re));
    doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), null, null))
      .when(okapiClient).doDelete(expectedUri, TEST_MODULE_ID);

    job.execute(jobExecutionContext);

    verify(okapiClient).doDelete(expectedUri, TEST_MODULE_ID);
  }

  @Test
  void execute_negative_unsupportedMethod() {
    var re = new RoutingEntry().path("/test-endpoint").methods(List.of("PATCH"));
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(systemJobDetail());
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(userImpersonationService.impersonate(TENANT_ID, SYSTEM_USER_ID)).thenReturn(USER_TOKEN);
    when(schedulerTimerService.getById(TIMER_UUID)).thenReturn(systemTimerDescriptor(re));

    job.execute(jobExecutionContext);

    verifyNoInteractions(okapiClient);
  }

  @Test
  void execute_negative_timerDescriptorNotFound() {
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(systemJobDetail());
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(userImpersonationService.impersonate(TENANT_ID, SYSTEM_USER_ID)).thenReturn(USER_TOKEN);
    when(schedulerTimerService.getById(TIMER_UUID)).thenThrow(new EntityNotFoundException("not found"));

    assertThatThrownBy(() -> job.execute(jobExecutionContext))
      .isInstanceOf(EntityNotFoundException.class);

    verifyNoInteractions(okapiClient);
  }

  private static JobDetail systemJobDetail() {
    return ScheduledJobDetail.builder()
      .id(TIMER_UUID).tenantId(TENANT_ID).moduleName(MODULE_NAME).timerType(TimerType.SYSTEM).build()
      .toQuartzJobDetail();
  }

  private static JobDetail systemJobDetailWithStaleUserId() {
    return ScheduledJobDetail.builder()
      .id(TIMER_UUID).tenantId(TENANT_ID).moduleName(MODULE_NAME).timerType(TimerType.SYSTEM)
      .userId(USER_ID_UUID).build()
      .toQuartzJobDetail();
  }

  private static JobDetail userJobDetail() {
    return ScheduledJobDetail.builder()
      .id(TIMER_UUID).tenantId(TENANT_ID).moduleName(MODULE_NAME).timerType(TimerType.USER).userId(USER_ID_UUID).build()
      .toQuartzJobDetail();
  }

  private static JobDetail userJobDetailWithoutUserId() {
    return ScheduledJobDetail.builder()
      .id(TIMER_UUID).tenantId(TENANT_ID).moduleName(MODULE_NAME).timerType(TimerType.USER).build()
      .toQuartzJobDetail();
  }

  private static TimerDescriptor systemTimerDescriptor(RoutingEntry re) {
    return TestValues.timerDescriptor().type(TimerType.SYSTEM).routingEntry(re).moduleId(TEST_MODULE_ID);
  }

  private static TimerDescriptor userTimerDescriptor(RoutingEntry re) {
    return TestValues.timerDescriptor().type(TimerType.USER).routingEntry(re).moduleName(TEST_MODULE_NAME);
  }
}
