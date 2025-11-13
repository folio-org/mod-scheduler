package org.folio.scheduler.service.jobs;

import static java.util.Collections.emptyMap;
import static java.util.Optional.of;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_UUID;
import static org.folio.scheduler.support.TestConstants.USER_TOKEN;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import feign.FeignException.NotFound;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestTemplate;
import java.util.List;
import java.util.Optional;
import org.folio.scheduler.configuration.properties.OkapiConfigurationProperties;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.integration.OkapiClient;
import org.folio.scheduler.integration.keycloak.SystemUserService;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.scheduler.service.UserImpersonationService;
import org.folio.scheduler.support.TestConstants;
import org.folio.scheduler.support.TestValues;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;
import org.quartz.impl.JobDetailImpl;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiHttpRequestExecutorTest {

  private static final String TEST_MODULE_ID = "mod-test-1.0";
  private static final String TEST_MODULE_NAME = "mod-test";

  private static final String OKAPI_URL = "http://okapi:9130";
  private static final String MODULE_NAME = "mod-scheduler";

  @InjectMocks private OkapiHttpRequestExecutor job;
  @Mock private OkapiClient okapiClient;
  @Mock private JobExecutionContext jobExecutionContext;
  @Mock private FolioModuleMetadata folioModuleMetadata;
  @Mock private SchedulerTimerService schedulerTimerService;
  @Mock private OkapiConfigurationProperties okapiConfigurationProperties;
  @Mock private UserImpersonationService userImpersonationService;
  @Mock private SystemUserService systemUserService;

  @BeforeEach
  void setUp() {
    when(userImpersonationService.impersonate(TENANT_ID, TestConstants.USER_ID)).thenReturn(USER_TOKEN);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(
      okapiClient, jobExecutionContext, schedulerTimerService, okapiConfigurationProperties);
  }

  @Test
  void execute_positive_userTokenIsNull() {
    var re = new RoutingEntry().methods(List.of("GET")).pathPattern("/test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(timerDescriptor(re)));
    when(userImpersonationService.impersonate(TENANT_ID, TestConstants.USER_ID)).thenReturn(null);

    job.execute(jobExecutionContext);

    verify(okapiClient).doGet(fromUriString("http://test-endpoint").build().toUri(), TEST_MODULE_ID);
  }

  @Test
  void execute_positive_userTokenIsNotNull() {
    var re = new RoutingEntry().methods(List.of("GET")).pathPattern("/test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(timerDescriptor(re)));

    job.execute(jobExecutionContext);

    verify(okapiClient).doGet(fromUriString("http://test-endpoint").build().toUri(), TEST_MODULE_ID);
  }

  @Test
  void execute_positive_methodNotDefined() {
    var re = new RoutingEntry().path("test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(timerDescriptor(re)));

    job.execute(jobExecutionContext);

    verify(okapiClient).doPost(fromUriString("http://test-endpoint").build().toUri(), TEST_MODULE_ID);
  }

  @Test
  void execute_positive_moduleNameAsHint() {
    var re = new RoutingEntry().methods(List.of("GET")).pathPattern("/test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(
      TestValues.timerDescriptor().routingEntry(re).moduleName(TEST_MODULE_NAME)));

    job.execute(jobExecutionContext);

    verify(okapiClient).doGet(fromUriString("http://test-endpoint").build().toUri(), TEST_MODULE_NAME);
  }

  @Test
  void execute_negative_feignException() {
    var re = new RoutingEntry().path("test-endpoint").methods(List.of("DELETE"));
    var expectedUri = fromUriString("http://test-endpoint").build().toUri();
    var request = Request.create(HttpMethod.DELETE, "http://test-endpoint", emptyMap(), null, (RequestTemplate) null);

    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(timerDescriptor(re)));
    doThrow(new NotFound("Not Found", request, null, emptyMap())).when(okapiClient).doDelete(expectedUri,
      TEST_MODULE_ID);

    job.execute(jobExecutionContext);

    verify(okapiClient).doDelete(expectedUri, TEST_MODULE_ID);
  }

  @Test
  void execute_negative_unsupportedMethod() {
    var re = new RoutingEntry().path("/test-endpoint").methods(List.of("PATCH"));

    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(timerDescriptor(re)));

    job.execute(jobExecutionContext);

    verifyNoInteractions(okapiClient);
  }

  @Test
  void execute_negative_timerDescriptorNotFound() {
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(Optional.empty());

    job.execute(jobExecutionContext);

    verifyNoInteractions(okapiClient);
  }

  @Test
  void execute_positive_userIdNotInJobDataMap() {
    var re = new RoutingEntry().methods(List.of("GET")).pathPattern("/test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetailWithoutUserId());
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(timerDescriptor(re)));
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(TestConstants.USER_ID);

    job.execute(jobExecutionContext);

    verify(okapiClient).doGet(fromUriString("http://test-endpoint").build().toUri(), TEST_MODULE_ID);
    verify(systemUserService).findSystemUserId(TENANT_ID);
  }

  @Test
  void execute_positive_userIdEmptyInJobDataMap() {
    var re = new RoutingEntry().methods(List.of("POST")).pathPattern("/test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetailWithEmptyUserId());
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(timerDescriptor(re)));
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(TestConstants.USER_ID);

    job.execute(jobExecutionContext);

    verify(okapiClient).doPost(fromUriString("http://test-endpoint").build().toUri(), TEST_MODULE_ID);
    verify(systemUserService).findSystemUserId(TENANT_ID);
  }

  private static JobDetailImpl jobDetail() {
    var jobDetail = new JobDetailImpl();
    jobDetail.setName(TIMER_ID);
    jobDetail.getJobDataMap().put(TENANT, TENANT_ID);
    jobDetail.getJobDataMap().put(XOkapiHeaders.USER_ID, TestConstants.USER_ID);
    return jobDetail;
  }

  private static JobDetailImpl jobDetailWithoutUserId() {
    var jobDetail = new JobDetailImpl();
    jobDetail.setName(TIMER_ID);
    jobDetail.getJobDataMap().put(TENANT, TENANT_ID);
    return jobDetail;
  }

  private static JobDetailImpl jobDetailWithEmptyUserId() {
    var jobDetail = new JobDetailImpl();
    jobDetail.setName(TIMER_ID);
    jobDetail.getJobDataMap().put(TENANT, TENANT_ID);
    jobDetail.getJobDataMap().put(XOkapiHeaders.USER_ID, "");
    return jobDetail;
  }

  private static TimerDescriptor timerDescriptor(RoutingEntry re) {
    return TestValues.timerDescriptor().type(TimerType.SYSTEM).routingEntry(re).moduleId(TEST_MODULE_ID);
  }
}
