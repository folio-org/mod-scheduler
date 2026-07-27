package org.folio.scheduler.service.jobs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.support.TestConstants.TIMER_UUID;
import static org.folio.scheduler.support.TestConstants.USER_ID;
import static org.folio.scheduler.support.TestConstants.USER_ID_UUID;
import static org.folio.scheduler.support.TestConstants.USER_TOKEN;
import static org.folio.scheduler.utils.TestUtils.OBJECT_MAPPER;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import jakarta.persistence.EntityNotFoundException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.StringMapMessage;
import org.folio.scheduler.configuration.TimerExecutionRetryConfiguration;
import org.folio.scheduler.configuration.properties.OkapiConfigurationProperties;
import org.folio.scheduler.configuration.properties.RetryConfigurationProperties;
import org.folio.scheduler.configuration.properties.RetryConfigurationProperties.RetryProperties;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiHttpRequestExecutorTest {

  private static final String TEST_MODULE_ID = "mod-test-1.0";
  private static final String TEST_MODULE_NAME = "mod-test";
  private static final String OKAPI_URL = "http://okapi:9130";
  private static final String MODULE_NAME = "mod-scheduler";
  private static final String SYSTEM_USER_ID = "99999999-9999-9999-9999-999999999999";

  private static final int RETRY_ATTEMPTS = 3;

  private OkapiHttpRequestExecutor job;
  @Mock private OkapiClient okapiClient;
  @Mock private JobExecutionContext jobExecutionContext;
  @Mock private FolioModuleMetadata folioModuleMetadata;
  @Mock private SchedulerTimerService schedulerTimerService;
  @Mock private OkapiConfigurationProperties okapiConfigurationProperties;
  @Mock private UserImpersonationService userImpersonationService;
  @Mock private SystemUserService systemUserService;

  private TestLogAppender logAppender;
  private Level originalLogLevel;

  @BeforeEach
  void setUp() {
    var logger = executorLogger();
    originalLogLevel = logger.getLevel();
    logAppender = new TestLogAppender();
    logAppender.start();
    logger.addAppender(logAppender);
    logger.setLevel(Level.INFO);
    job = newExecutor(RETRY_ATTEMPTS);
  }

  /**
   * Builds the executor with the production retry assembly, only with negligible backoff so tests stay fast.
   */
  private OkapiHttpRequestExecutor newExecutor(int retryAttempts) {
    var properties = new RetryConfigurationProperties();
    properties.setConfig(Map.of("timer-execution", RetryProperties.of(ofMillis(1), ofMillis(2), retryAttempts, 2)));
    var classifier = new TimerExecutionRetryClassifier(OBJECT_MAPPER);
    var retryTemplate = new TimerExecutionRetryConfiguration()
      .timerExecutionRetryTemplate(properties, classifier);

    return new OkapiHttpRequestExecutor(okapiClient, folioModuleMetadata, schedulerTimerService,
      okapiConfigurationProperties, userImpersonationService, systemUserService, retryTemplate, classifier);
  }

  @AfterEach
  void tearDown() {
    var logger = executorLogger();
    logger.removeAppender(logAppender);
    logger.setLevel(originalLogLevel);
    logAppender.stop();
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
    assertStartedLog(assertSystemTimerEvent("timer.execution.start", "GET", "/test-endpoint"));
    assertSuccessLog(assertSystemTimerEvent("timer.execution.success", "GET", "/test-endpoint"));
    assertLoggedMessagesDoNotContain(USER_TOKEN, SYSTEM_USER_ID);
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
    assertSuccessLog(assertUserTimerEvent("timer.execution.success", "GET", "/test-endpoint"));
    assertLoggedMessagesDoNotContain(USER_TOKEN, USER_ID);
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
    var responseBody = "downstream body token=body-secret".getBytes(UTF_8);
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(systemJobDetail());
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(userImpersonationService.impersonate(TENANT_ID, SYSTEM_USER_ID)).thenReturn(USER_TOKEN);
    when(schedulerTimerService.getById(TIMER_UUID)).thenReturn(systemTimerDescriptor(re));
    doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found token=downstream-secret",
      new HttpHeaders(), responseBody, UTF_8))
      .when(okapiClient).doDelete(expectedUri, TEST_MODULE_ID);

    job.execute(jobExecutionContext);

    verify(okapiClient).doDelete(expectedUri, TEST_MODULE_ID);
    var event = assertSystemTimerEvent("timer.execution.failure", "DELETE", "/test-endpoint", "test-endpoint");
    assertHttpStatusFailureLog(event);
    assertLoggedMessagesDoNotContain(USER_TOKEN, SYSTEM_USER_ID, "downstream-secret", "body-secret");
  }

  @Test
  void execute_negative_restClientException() {
    var re = new RoutingEntry().path("test-endpoint").methods(List.of("POST"));
    var expectedUri = fromUriString("http://test-endpoint").build().toUri();
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(systemJobDetail());
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(userImpersonationService.impersonate(TENANT_ID, SYSTEM_USER_ID)).thenReturn(USER_TOKEN);
    when(schedulerTimerService.getById(TIMER_UUID)).thenReturn(systemTimerDescriptor(re));
    doThrow(new ResourceAccessException("I/O error token=transport-secret",
      new SocketTimeoutException("Read timed out token=transport-secret")))
      .when(okapiClient).doPost(expectedUri, TEST_MODULE_ID);

    job.execute(jobExecutionContext);

    // a read timeout is deliberately not retried: the first request is most likely still running
    verify(okapiClient).doPost(expectedUri, TEST_MODULE_ID);
    var event = assertSystemTimerEvent("timer.execution.failure", "POST", "/test-endpoint", "test-endpoint");
    assertRestClientFailureLog(event);
    assertLoggedMessagesDoNotContain(USER_TOKEN, SYSTEM_USER_ID, "transport-secret");
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
    assertUnsupportedMethodLog(assertSystemTimerEvent("timer.execution.failure", "PATCH", "/test-endpoint"));
    assertThat(logAppender.timerEvents())
      .noneMatch(event -> "timer.execution.start".equals(event.get("event")));
  }

  @Test
  void execute_positive_retriesWithStructuredLogAfterTransientFailure() {
    var re = new RoutingEntry().path("test-endpoint").methods(List.of("POST"));
    var expectedUri = fromUriString("http://test-endpoint").build().toUri();
    stubSystemTimer(re);
    doThrow(serverError(HttpStatus.SERVICE_UNAVAILABLE, authorizationErrorBody())).doNothing()
      .when(okapiClient).doPost(expectedUri, TEST_MODULE_ID);

    job.execute(jobExecutionContext);

    verify(okapiClient, times(2)).doPost(expectedUri, TEST_MODULE_ID);
    verify(userImpersonationService).impersonate(TENANT_ID, SYSTEM_USER_ID);
    assertSuccessLog(assertSystemTimerEvent("timer.execution.success", "POST", "/test-endpoint", "test-endpoint"));
    assertThat(assertSystemTimerEvent("timer.execution.retry", "POST", "/test-endpoint", "test-endpoint"))
      .containsEntry("outcome", "RETRY")
      .containsEntry("retryNumber", 1)
      .containsEntry("reason", "AUTHORIZATION_SERVICE_UNAVAILABLE");
    assertLoggedMessagesDoNotContain("authorization-secret");
  }

  @Test
  void execute_negative_doesNotRetryGenericServerFailure() {
    var re = new RoutingEntry().path("test-endpoint").methods(List.of("POST"));
    var expectedUri = fromUriString("http://test-endpoint").build().toUri();
    stubSystemTimer(re);
    doThrow(serverError(HttpStatus.SERVICE_UNAVAILABLE, "")).when(okapiClient)
      .doPost(expectedUri, TEST_MODULE_ID);

    job.execute(jobExecutionContext);

    verify(okapiClient).doPost(expectedUri, TEST_MODULE_ID);
    assertThat(assertSystemTimerEvent("timer.execution.failure", "POST", "/test-endpoint", "test-endpoint"))
      .containsEntry("outcome", "FAILURE");
    assertThat(logAppender.timerEvents()).noneMatch(event -> "timer.execution.retry".equals(event.get("event")));
  }

  @Test
  void execute_negative_doesNotRetryForbiddenResponse() {
    var re = new RoutingEntry().path("test-endpoint").methods(List.of("POST"));
    var expectedUri = fromUriString("http://test-endpoint").build().toUri();
    stubSystemTimer(re);
    doThrow(clientError(HttpStatus.FORBIDDEN)).when(okapiClient).doPost(expectedUri, TEST_MODULE_ID);

    job.execute(jobExecutionContext);

    verify(okapiClient).doPost(expectedUri, TEST_MODULE_ID);
    assertThat(logAppender.timerEvents()).noneMatch(event -> "timer.execution.retry".equals(event.get("event")));
  }

  @Test
  void execute_negative_logsEachRetryAndOneFailureWhenRetriesAreExhausted() {
    var re = new RoutingEntry().path("test-endpoint").methods(List.of("POST"));
    var expectedUri = fromUriString("http://test-endpoint").build().toUri();
    stubSystemTimer(re);
    doThrow(serverError(HttpStatus.SERVICE_UNAVAILABLE, authorizationErrorBody())).when(okapiClient)
      .doPost(expectedUri, TEST_MODULE_ID);

    job.execute(jobExecutionContext);

    verify(okapiClient, times(RETRY_ATTEMPTS)).doPost(expectedUri, TEST_MODULE_ID);
    assertThat(logAppender.timerEvents())
      .filteredOn(event -> "timer.execution.retry".equals(event.get("event")))
      .extracting(event -> event.get("retryNumber"))
      .containsExactly(1, 2);
    assertThat(logAppender.timerEvents())
      .filteredOn(event -> "timer.execution.failure".equals(event.get("event")))
      .singleElement()
      .satisfies(event -> assertThat(event).containsEntry("outcome", "FAILURE"));
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

  private void stubSystemTimer(RoutingEntry re) {
    when(folioModuleMetadata.getModuleName()).thenReturn(MODULE_NAME);
    when(okapiConfigurationProperties.getUrl()).thenReturn(OKAPI_URL);
    when(jobExecutionContext.getJobDetail()).thenReturn(systemJobDetail());
    when(systemUserService.findSystemUserId(TENANT_ID)).thenReturn(SYSTEM_USER_ID);
    when(userImpersonationService.impersonate(TENANT_ID, SYSTEM_USER_ID)).thenReturn(USER_TOKEN);
    when(schedulerTimerService.getById(TIMER_UUID)).thenReturn(systemTimerDescriptor(re));
  }

  private static HttpStatusCodeException clientError(HttpStatus status) {
    return clientError(status, "");
  }

  private static HttpStatusCodeException clientError(HttpStatus status, String body) {
    return HttpClientErrorException.create(status, status.getReasonPhrase(), new HttpHeaders(), body.getBytes(UTF_8),
      UTF_8);
  }

  private static HttpStatusCodeException serverError(HttpStatus status, String body) {
    return HttpServerErrorException.create(status, status.getReasonPhrase(), new HttpHeaders(), body.getBytes(UTF_8),
      UTF_8);
  }

  private static String authorizationErrorBody() {
    return """
      {"errors":[{"code":"authorization_error","message":"authorization-secret"}],"total_records":1}
      """;
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
    return TestValues.timerDescriptor().type(TimerType.SYSTEM).routingEntry(re)
      .moduleName(TEST_MODULE_NAME).moduleId(TEST_MODULE_ID);
  }

  private static TimerDescriptor userTimerDescriptor(RoutingEntry re) {
    return TestValues.timerDescriptor().type(TimerType.USER).routingEntry(re).moduleName(TEST_MODULE_NAME);
  }

  private Map<String, Object> timerEvent(String eventName) {
    return logAppender.timerEvents().stream()
      .filter(event -> eventName.equals(event.get("event")))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Expected timer log event: " + eventName));
  }

  private void assertLoggedMessagesDoNotContain(String... values) {
    var messages = logAppender.messages();
    for (var value : values) {
      assertThat(messages).noneMatch(message -> message.contains(value));
    }
  }

  private Map<String, Object> assertSystemTimerEvent(String eventName, String method, String path) {
    return assertSystemTimerEvent(eventName, method, path, path);
  }

  private Map<String, Object> assertSystemTimerEvent(String eventName, String method, String path,
    String naturalKeyPath) {
    return assertTimerEvent(eventName, TimerType.SYSTEM, TEST_MODULE_NAME, TEST_MODULE_ID, method, path,
      naturalKeyPath);
  }

  private Map<String, Object> assertUserTimerEvent(String eventName, String method, String path) {
    return assertTimerEvent(eventName, TimerType.USER, TEST_MODULE_NAME, "", method, path, path);
  }

  private Map<String, Object> assertTimerEvent(String eventName, TimerType type, String moduleName, String moduleId,
    String method, String path, String naturalKeyPath) {
    var event = timerEvent(eventName);
    assertThat(event)
      .containsEntry("timerId", TIMER_UUID.toString())
      .containsEntry("naturalKey", type + "#" + moduleName + "#" + method + "#" + naturalKeyPath)
      .containsEntry("type", type)
      .containsEntry("moduleName", moduleName)
      .containsEntry("moduleId", moduleId)
      .containsEntry("tenant", TENANT_ID)
      .containsEntry("method", method)
      .containsEntry("path", path);
    return event;
  }

  private static void assertStartedLog(Map<String, Object> event) {
    assertThat(event)
      .containsEntry("outcome", "STARTED")
      .doesNotContainKeys("status", "durationMs");
  }

  private static void assertSuccessLog(Map<String, Object> event) {
    assertThat(event)
      .containsEntry("outcome", "SUCCESS")
      .doesNotContainKey("status")
      .containsKey("durationMs");
    assertThat(event.get("durationMs").toString()).matches("\\d+");
  }

  private static void assertHttpStatusFailureLog(Map<String, Object> event) {
    assertThat(event)
      .containsEntry("status", 404)
      .containsEntry("outcome", "FAILURE")
      .containsKey("durationMs")
      .containsKey("errorClass")
      .doesNotContainKey("errorMessage");
  }

  private static void assertRestClientFailureLog(Map<String, Object> event) {
    assertThat(event)
      .containsEntry("outcome", "FAILURE")
      .containsEntry("rootCauseClass", "SocketTimeoutException")
      .containsKey("durationMs")
      .containsKey("errorClass")
      .doesNotContainKeys("status", "errorMessage", "rootCauseMessage");
  }

  private static void assertUnsupportedMethodLog(Map<String, Object> event) {
    assertThat(event)
      .containsEntry("outcome", "UNSUPPORTED_METHOD")
      .doesNotContainKeys("status", "durationMs", "errorClass", "errorMessage");
  }

  private static Logger executorLogger() {
    return (Logger) LogManager.getLogger(OkapiHttpRequestExecutor.class);
  }

  private static final class TestLogAppender extends AbstractAppender {

    private final List<LogEvent> events = new ArrayList<>();

    private TestLogAppender() {
      super("test-log-appender", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      events.add(event.toImmutable());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> timerEvents() {
      return events.stream()
        .map(LogEvent::getMessage)
        .filter(StringMapMessage.class::isInstance)
        .map(StringMapMessage.class::cast)
        .map(message -> (Map<String, Object>) (Map<?, ?>) message.getData())
        .toList();
    }

    private List<String> messages() {
      return events.stream()
        .map(event -> event.getMessage().getFormattedMessage())
        .toList();
    }
  }
}
