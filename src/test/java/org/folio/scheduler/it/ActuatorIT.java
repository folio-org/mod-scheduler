package org.folio.scheduler.it;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableKeycloak;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;

@EnableKeycloak
@IntegrationTest
class ActuatorIT extends BaseIntegrationTest {

  @Test
  void getContainerHealth_positive() throws Exception {
    doGet("/admin/health").andExpect(jsonPath("$.status", is("UP")));
  }
}
