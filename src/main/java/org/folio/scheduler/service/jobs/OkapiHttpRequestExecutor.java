package org.folio.scheduler.service.jobs;

import static java.util.Collections.singletonList;
import static java.util.Map.entry;
import static java.util.concurrent.ThreadLocalRandom.current;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.spring.integration.XOkapiHeaders.REQUEST_ID;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.TOKEN;
import static org.folio.spring.integration.XOkapiHeaders.URL;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.message.StringMapMessage;
import org.folio.scheduler.configuration.properties.OkapiConfigurationProperties;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerType;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.integration.OkapiClient;
import org.folio.scheduler.integration.keycloak.SystemUserService;
import org.folio.scheduler.service.ScheduledJobDetail;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.scheduler.service.UserImpersonationService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

@Log4j2
@Component
public class OkapiHttpRequestExecutor implements Job {

  private final FolioModuleMetadata folioModuleMetadata;
  private final SchedulerTimerService schedulerTimerService;
  private final OkapiConfigurationProperties okapiConfigurationProperties;
  private final Map<HttpMethod, BiConsumer<URI, String>> okapiCallMap;
  private final UserImpersonationService userImpersonationService;
  private final SystemUserService systemUserService;

  /**
   * Injects required spring components into {@link OkapiHttpRequestExecutor} bean.
   *
   * @param okapiClient - {@link OkapiClient} feign client
   * @param folioModuleMetadata - {@link FolioModuleMetadata} component
   * @param schedulerTimerService - {@link SchedulerTimerService} service
   * @param okapiConfigurationProperties - {@link OkapiConfigurationProperties} component
   */
  public OkapiHttpRequestExecutor(OkapiClient okapiClient, FolioModuleMetadata folioModuleMetadata,
    SchedulerTimerService schedulerTimerService, OkapiConfigurationProperties okapiConfigurationProperties,
    UserImpersonationService userImpersonationService, SystemUserService systemUserService) {
    this.folioModuleMetadata = folioModuleMetadata;
    this.schedulerTimerService = schedulerTimerService;
    this.okapiConfigurationProperties = okapiConfigurationProperties;
    this.userImpersonationService = userImpersonationService;
    this.systemUserService = systemUserService;

    this.okapiCallMap = Map.ofEntries(
      entry(GET, okapiClient::doGet),
      entry(POST, okapiClient::doPost),
      entry(PUT, okapiClient::doPut),
      entry(DELETE, okapiClient::doDelete)
    );
  }

  @Override
  public void execute(JobExecutionContext context) {
    var jobDetail = ScheduledJobDetail.fromQuartzJobDetail(context.getJobDetail());

    var allHeaders = prepareAllHeadersMap(jobDetail);
    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, allHeaders)) {
      var timerDescriptor = schedulerTimerService.getById(jobDetail.getId());

      callHttpMethod(timerDescriptor, jobDetail.getTenantId());
    }
  }

  private void callHttpMethod(TimerDescriptor timerDescriptor, String tenant) {
    var re = timerDescriptor.getRoutingEntry();
    var httpMethod = isNotEmpty(re.getMethods()) ? HttpMethod.valueOf(re.getMethods().getFirst().toUpperCase()) : POST;
    var staticPath = getStaticPath(re);
    var moduleHint = moduleHint(timerDescriptor);
    var logContext = TimerExecutionLogContext.from(timerDescriptor, tenant, httpMethod, staticPath);

    var okapiCallExecutor = okapiCallMap.get(httpMethod);
    if (okapiCallExecutor == null) {
      logUnsupportedMethod(logContext);
      return;
    }

    logStart(logContext);
    var startNanos = System.nanoTime();
    try {
      okapiCallExecutor.accept(fromUriString("http:/" + staticPath).build().toUri(), moduleHint);
      logSuccess(logContext, startNanos);
    } catch (RestClientException e) {
      logFailure(logContext, startNanos, e);
    }
  }

  private void logUnsupportedMethod(TimerExecutionLogContext logContext) {
    log.warn(timerExecutionMessage("timer.execution.failure", logContext)
      .with("outcome", "UNSUPPORTED_METHOD"));
  }

  private void logStart(TimerExecutionLogContext logContext) {
    log.info(timerExecutionMessage("timer.execution.start", logContext)
      .with("outcome", "STARTED"));
  }

  private void logSuccess(TimerExecutionLogContext logContext, long startNanos) {
    log.info(timerExecutionMessage("timer.execution.success", logContext)
      .with("outcome", "SUCCESS")
      .with("durationMs", durationMs(startNanos)));
  }

  private void logFailure(TimerExecutionLogContext logContext, long startNanos, RestClientException exception) {
    var message = timerExecutionMessage("timer.execution.failure", logContext)
      .with("outcome", "FAILURE")
      .with("durationMs", durationMs(startNanos))
      .with("errorClass", exception.getClass().getSimpleName());

    if (exception instanceof HttpStatusCodeException statusException) {
      message.with("status", statusException.getStatusCode().value());
    }

    var rootCause = exception.getRootCause();
    if (rootCause != null) {
      message.with("rootCauseClass", rootCause.getClass().getSimpleName());
    }

    log.warn(message);
  }

  private StringMapMessage timerExecutionMessage(String event, TimerExecutionLogContext context) {
    return new StringMapMessage()
      .with("event", event)
      .with("timerId", context.timerId())
      .with("naturalKey", context.naturalKey())
      .with("type", context.type())
      .with("moduleName", context.moduleName())
      .with("moduleId", context.moduleId())
      .with("tenant", context.tenant())
      .with("method", context.method().name())
      .with("path", context.path());
  }

  private String durationMs(long startNanos) {
    return String.valueOf(NANOSECONDS.toMillis(System.nanoTime() - startNanos));
  }

  private static String moduleHint(TimerDescriptor td) {
    return td.getType() == TimerType.USER ? td.getModuleName() : moduleIdOrName(td);
  }

  private static String moduleIdOrName(TimerDescriptor td) {
    return StringUtils.isNotEmpty(td.getModuleId()) ? td.getModuleId() : td.getModuleName();
  }

  private static String getStaticPath(RoutingEntry re) {
    var resolvedPath = isEmpty(re.getPath()) ? re.getPathPattern() : re.getPath();
    return resolvedPath.startsWith("/") ? resolvedPath : "/" + resolvedPath;
  }

  @SuppressWarnings("java:S2245")
  private Map<String, Collection<String>> prepareAllHeadersMap(ScheduledJobDetail jobDetail) {
    var headers = new HashMap<String, Collection<String>>();
    var tenant = jobDetail.getTenantId();
    var userId = getUserId(jobDetail);
    var userToken = userImpersonationService.impersonate(tenant, userId);
    validateUserToken(userToken, tenant, userId);

    headers.put(URL, singletonList(okapiConfigurationProperties.getUrl()));
    headers.put(TOKEN, singletonList(userToken));
    headers.put(REQUEST_ID, singletonList(String.format("%06d", current().nextInt(1000000))));
    headers.put(TENANT, singletonList(tenant));
    return headers;
  }

  private static void validateUserToken(String userToken, String tenant, String userId) {
    if (isBlank(userToken) || "null".equalsIgnoreCase(userToken.trim())) {
      throw new IllegalStateException("Failed to prepare timer request: user impersonation token is blank [tenant: "
        + tenant + ", userId: " + userId + "]");
    }
  }

  private String getUserId(ScheduledJobDetail jobDetail) {
    return switch (jobDetail.getTimerType()) {
      case USER -> {
        if (jobDetail.getUserId() == null) {
          throw new IllegalStateException("Failed to prepare timer request: userId is null for user timer [tenant: "
            + jobDetail.getTenantId() + "]");
        }
        yield jobDetail.getUserId().toString();
      }
      case SYSTEM -> systemUserService.findSystemUserId(jobDetail.getTenantId());
    };
  }

  private record TimerExecutionLogContext(String timerId, String naturalKey, TimerType type, String moduleName,
                                          String moduleId, String tenant, HttpMethod method, String path) {

    private static TimerExecutionLogContext from(TimerDescriptor descriptor, String tenant, HttpMethod method,
      String path) {
      return new TimerExecutionLogContext(
        Objects.toString(descriptor.getId(), ""),
        naturalKey(descriptor),
        descriptor.getType(),
        Objects.toString(descriptor.getModuleName(), ""),
        Objects.toString(descriptor.getModuleId(), ""),
        Objects.toString(tenant, ""),
        method,
        Objects.toString(path, ""));
    }

    private static String naturalKey(TimerDescriptor descriptor) {
      try {
        return Objects.toString(TimerDescriptorEntity.toNaturalKey(descriptor), "");
      } catch (IllegalArgumentException exception) {
        return "";
      }
    }
  }
}
