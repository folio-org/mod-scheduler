package org.folio.scheduler.service.jobs;

import static feign.Request.HttpMethod.DELETE;
import static feign.Request.HttpMethod.GET;
import static feign.Request.HttpMethod.POST;
import static feign.Request.HttpMethod.PUT;
import static java.util.Collections.singletonList;
import static java.util.Map.entry;
import static java.util.concurrent.ThreadLocalRandom.current;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.spring.integration.XOkapiHeaders.REQUEST_ID;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.TOKEN;
import static org.folio.spring.integration.XOkapiHeaders.URL;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import feign.FeignException;
import feign.Request.HttpMethod;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.scheduler.configuration.properties.OkapiConfigurationProperties;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.integration.OkapiClient;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.scheduler.service.UserImpersonationService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class OkapiHttpRequestExecutor implements Job {

  private final FolioModuleMetadata folioModuleMetadata;
  private final SchedulerTimerService schedulerTimerService;
  private final OkapiConfigurationProperties okapiConfigurationProperties;
  private final Map<HttpMethod, BiConsumer<URI, String>> okapiCallMap;
  private final UserImpersonationService userImpersonationService;

  /**
   * Injects required spring components into {@link OkapiHttpRequestExecutor} bean.
   *
   * @param okapiClient - {@link OkapiClient} feign client
   * @param folioModuleMetadata - {@link FolioModuleMetadata} component
   * @param schedulerTimerService - {@link SchedulerTimerService} service
   * @param okapiConfigurationProperties - {@link OkapiConfigurationProperties} component
   */
  public OkapiHttpRequestExecutor(OkapiClient okapiClient, FolioModuleMetadata folioModuleMetadata,
    SchedulerTimerService schedulerTimerService,
    OkapiConfigurationProperties okapiConfigurationProperties,
    UserImpersonationService userImpersonationService) {
    this.folioModuleMetadata = folioModuleMetadata;
    this.schedulerTimerService = schedulerTimerService;
    this.okapiConfigurationProperties = okapiConfigurationProperties;
    this.userImpersonationService = userImpersonationService;

    this.okapiCallMap = Map.ofEntries(
      entry(GET, okapiClient::doGet),
      entry(POST, okapiClient::doPost),
      entry(PUT, okapiClient::doPut),
      entry(DELETE, okapiClient::doDelete)
    );
  }

  @Override
  public void execute(JobExecutionContext context) {
    var jobDetail = context.getJobDetail();
    var timerId = UUID.fromString(jobDetail.getKey().getName());

    var allHeaders = prepareAllHeadersMap(jobDetail.getJobDataMap());
    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, allHeaders)) {
      var timerDescriptor = schedulerTimerService.findById(timerId);

      if (timerDescriptor.isEmpty()) {
        log.warn("Failed to find timer descriptor [timerId: {}]", timerId);
        return;
      }

      callHttpMethod(timerDescriptor.get());
    }
  }

  private void callHttpMethod(TimerDescriptor timerDescriptor) {
    var timerId = timerDescriptor.getId();
    var re = timerDescriptor.getRoutingEntry();
    var methods = re.getMethods();
    var httpMethod = isNotEmpty(methods) ? HttpMethod.valueOf(methods.get(0)) : POST;

    var okapiCallExecutor = okapiCallMap.get(httpMethod);
    if (okapiCallExecutor == null) {
      log.warn("Unsupported HTTP method for timer [id: {}, method: {}]", timerId, httpMethod);
      return;
    }

    var staticPath = getStaticPath(re);
    var moduleHint = moduleHint(timerDescriptor);
    log.info("Calling specified HTTP method [timerId: {}, method: {}, path: {}, moduleHint: {}]",
      timerId, httpMethod, staticPath, moduleHint);
    try {
      okapiCallExecutor.accept(fromUriString("http:/" + staticPath).build().toUri(), moduleHint);
    } catch (FeignException e) {
      log.warn("Failed to perform HTTP request [id: {}, method: {}, path: /{}]", timerId, httpMethod, staticPath, e);
    }
  }

  private static String moduleHint(TimerDescriptor td) {
    return StringUtils.isNotEmpty(td.getModuleId()) ? td.getModuleId() : td.getModuleName();
  }

  private static String getStaticPath(RoutingEntry re) {
    var resolvedPath = StringUtils.isEmpty(re.getPath()) ? re.getPathPattern() : re.getPath();
    return resolvedPath.startsWith("/") ? resolvedPath : "/" + resolvedPath;
  }

  private Map<String, Collection<String>> prepareAllHeadersMap(JobDataMap jobDataMap) {
    var headers = new HashMap<String, Collection<String>>();
    var tenant = (String) jobDataMap.get(TENANT);
    var userId = (String) jobDataMap.get(USER_ID);
    headers.put(URL, singletonList(okapiConfigurationProperties.getUrl()));
    headers.put(TOKEN, singletonList(userImpersonationService.impersonate(tenant, userId)));
    headers.put(REQUEST_ID, singletonList(String.format("%06d", current().nextInt(1000000))));
    headers.put(TENANT, singletonList(tenant));
    return headers;
  }
}
