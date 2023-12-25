package org.folio.scheduler.support.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.USER_ID;
import static org.folio.test.TestUtils.asJsonString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.SneakyThrows;
import org.apache.kafka.clients.admin.NewTopic;
import org.folio.scheduler.support.TestConstants;
import org.folio.scheduler.support.extension.EnablePostgres;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.folio.test.base.BaseBackendIntegrationTest;
import org.folio.test.extensions.EnableKafka;
import org.folio.test.extensions.EnableWireMock;
import org.folio.test.extensions.impl.WireMockAdminClient;
import org.folio.test.extensions.impl.WireMockAdminClient.RequestCriteria;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@EnableKafka
@EnableWireMock
@EnablePostgres
@SpringBootTest
@ActiveProfiles("it")
@AutoConfigureMockMvc
@DirtiesContext(classMode = AFTER_CLASS)
public abstract class BaseIntegrationTest extends BaseBackendIntegrationTest {

  protected static WireMockAdminClient wmAdminClient;

  public static ResultActions attemptGet(String uri, Object... args) throws Exception {
    return mockMvc.perform(get(uri, args)
      .header(TENANT, TENANT_ID)
      .contentType(APPLICATION_JSON));
  }

  protected static ResultActions attemptPost(String uri, Object body, Object... args) throws Exception {
    return mockMvc.perform(post(uri, args)
      .header(TENANT, TENANT_ID)
      .header(USER_ID, TestConstants.USER_ID)
      .content(asJsonString(body))
      .contentType(APPLICATION_JSON));
  }

  protected static ResultActions attemptPut(String uri, Object body, Object... args) throws Exception {
    return mockMvc.perform(put(uri, args)
      .header(TENANT, TENANT_ID)
      .content(asJsonString(body))
      .contentType(APPLICATION_JSON));
  }

  protected static ResultActions attemptDelete(String uri, Object... args) throws Exception {
    return mockMvc.perform(delete(uri, args)
      .header(TENANT, TENANT_ID)
      .contentType(APPLICATION_JSON));
  }

  public static ResultActions doGet(String uri, Object... args) throws Exception {
    return attemptGet(uri, args).andExpect(status().isOk());
  }

  public static ResultActions doGet(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request.contentType(APPLICATION_JSON)).andExpect(status().isOk());
  }

  protected static ResultActions doPost(String uri, Object body, Object... args) throws Exception {
    return attemptPost(uri, body, args).andExpect(status().isCreated());
  }

  protected static ResultActions doPut(String uri, Object body, Object... args) throws Exception {
    return attemptPut(uri, body, args).andExpect(status().isOk());
  }

  protected static ResultActions doDelete(String uri, Object... args) throws Exception {
    return attemptDelete(uri, args).andExpect(status().isNoContent());
  }

  @SneakyThrows
  protected static <T> void setUpTenant() {
    enableTenant(TENANT_ID);
  }

  @SneakyThrows
  protected static <T> void setUpTenant(String tenant) {
    enableTenant(tenant);
  }

  @SneakyThrows
  protected static void enableTenant(String tenantId) {
    mockMvc.perform(post("/_/tenant")
        .content(asJsonString(new TenantAttributes().moduleTo("mod-scheduler")))
        .headers(defaultHeaders(tenantId))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  @SneakyThrows
  protected static void removeTenant() {
    removeTenant(TENANT_ID);
  }

  @SneakyThrows
  protected static void removeTenant(String tenantId) {
    mockMvc.perform(post("/_/tenant")
        .content(asJsonString(new TenantAttributes().moduleFrom("mod-scheduler").purge(true)))
        .headers(defaultHeaders(tenantId)))
      .andExpect(status().isNoContent());
  }

  public static HttpHeaders defaultHeaders(String tenant) {
    var httpHeaders = new HttpHeaders();

    httpHeaders.setContentType(APPLICATION_JSON);
    httpHeaders.add(XOkapiHeaders.TENANT, tenant);

    return httpHeaders;
  }

  protected static void createTopic(String topicName, KafkaAdmin kafkaAdmin) {
    kafkaAdmin.createOrModifyTopics(new NewTopic(topicName, 1, (short) 1));
  }

  protected static void verifyTimerRequestCallsCount() {
    var count = wmAdminClient.requestCount(RequestCriteria.builder()
      .urlPath("/test")
      .method(HttpMethod.POST)
      .build());

    assertThat(count).isPositive();
  }
}
