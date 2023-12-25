package org.folio.scheduler.service.jobs;

import static java.util.Collections.emptyMap;
import static java.util.Optional.of;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_UUID;
import static org.folio.scheduler.support.TestConstants.USER_TOKEN;
import static org.folio.scheduler.support.TestValues.timerDescriptor;
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
import org.folio.scheduler.integration.OkapiClient;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.scheduler.service.UserImpersonationService;
import org.folio.scheduler.support.TestConstants;
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

  private final String okapiUrl = "http://okapi:9130";
  private final String moduleName = "mod-scheduler";

  @InjectMocks private OkapiHttpRequestExecutor job;
  @Mock private OkapiClient okapiClient;
  @Mock private JobExecutionContext jobExecutionContext;
  @Mock private FolioModuleMetadata folioModuleMetadata;
  @Mock private SchedulerTimerService schedulerTimerService;
  @Mock private OkapiConfigurationProperties okapiConfigurationProperties;
  @Mock private UserImpersonationService userImpersonationService;

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
    when(folioModuleMetadata.getModuleName()).thenReturn(moduleName);
    when(okapiConfigurationProperties.getUrl()).thenReturn(okapiUrl);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(timerDescriptor().routingEntry(re)));
    when(userImpersonationService.impersonate(TENANT_ID, TestConstants.USER_ID)).thenReturn(null);

    job.execute(jobExecutionContext);

    verify(okapiClient).doGet(fromUriString("http://test-endpoint").build().toUri());
  }

  @Test
  void execute_positive_userTokenIsNotNull() {
    var re = new RoutingEntry().methods(List.of("GET")).pathPattern("/test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(moduleName);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(okapiConfigurationProperties.getUrl()).thenReturn(okapiUrl);
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(timerDescriptor().routingEntry(re)));

    job.execute(jobExecutionContext);

    verify(okapiClient).doGet(fromUriString("http://test-endpoint").build().toUri());
  }

  @Test
  void execute_positive_methodNotDefined() {
    var re = new RoutingEntry().path("test-endpoint");
    when(folioModuleMetadata.getModuleName()).thenReturn(moduleName);
    when(okapiConfigurationProperties.getUrl()).thenReturn(okapiUrl);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(timerDescriptor().routingEntry(re)));

    job.execute(jobExecutionContext);

    verify(okapiClient).doPost(fromUriString("http://test-endpoint").build().toUri());
  }

  @Test
  void execute_negative_feignException() {
    var re = new RoutingEntry().path("test-endpoint").methods(List.of("DELETE"));
    var expectedUri = fromUriString("http://test-endpoint").build().toUri();
    var request = Request.create(HttpMethod.DELETE, "http://test-endpoint", emptyMap(), null, (RequestTemplate) null);

    when(folioModuleMetadata.getModuleName()).thenReturn(moduleName);
    when(okapiConfigurationProperties.getUrl()).thenReturn(okapiUrl);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(okapiConfigurationProperties.getUrl()).thenReturn(okapiUrl);
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(timerDescriptor().routingEntry(re)));
    doThrow(new NotFound("Not Found", request, null, emptyMap())).when(okapiClient).doDelete(expectedUri);

    job.execute(jobExecutionContext);

    verify(okapiClient).doDelete(expectedUri);
  }

  @Test
  void execute_negative_unsupportedMethod() {
    var re = new RoutingEntry().path("/test-endpoint").methods(List.of("PATCH"));

    when(folioModuleMetadata.getModuleName()).thenReturn(moduleName);
    when(okapiConfigurationProperties.getUrl()).thenReturn(okapiUrl);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(of(timerDescriptor().routingEntry(re)));

    job.execute(jobExecutionContext);

    verifyNoInteractions(okapiClient);
  }

  @Test
  void execute_negative_timerDescriptorNotFound() {
    when(folioModuleMetadata.getModuleName()).thenReturn(moduleName);
    when(okapiConfigurationProperties.getUrl()).thenReturn(okapiUrl);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail());
    when(schedulerTimerService.findById(TIMER_UUID)).thenReturn(Optional.empty());

    job.execute(jobExecutionContext);

    verifyNoInteractions(okapiClient);
  }

  private static JobDetailImpl jobDetail() {
    var jobDetail = new JobDetailImpl();
    jobDetail.setName(TIMER_ID);
    jobDetail.getJobDataMap().put(TENANT, TENANT_ID);
    jobDetail.getJobDataMap().put(XOkapiHeaders.USER_ID, TestConstants.USER_ID);
    return jobDetail;
  }
}
