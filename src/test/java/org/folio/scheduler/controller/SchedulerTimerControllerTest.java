package org.folio.scheduler.controller;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.scheduler.support.TestValues.timerDescriptor;
import static org.folio.test.TestUtils.asJsonString;
import static org.folio.test.TestUtils.parseResponse;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerDescriptorList;
import org.folio.scheduler.domain.model.SearchResult;
import org.folio.scheduler.exception.RequestValidationException;
import org.folio.scheduler.service.SchedulerTimerService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@Import(ApiExceptionHandler.class)
@WebMvcTest(SchedulerTimerController.class)
class SchedulerTimerControllerTest {

  private static final UUID TIMER_UUID = randomUUID();

  @Autowired private MockMvc mockMvc;
  @MockBean private SchedulerTimerService schedulingTimerService;

  @Test
  void get_positive() throws Exception {
    var timerDescriptor = new TimerDescriptor().id(TIMER_UUID).enabled(true);
    when(schedulingTimerService.getById(TIMER_UUID)).thenReturn(timerDescriptor);
    var mvcResult = mockMvc.perform(get("/scheduler/timers/{id}", TIMER_UUID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    var actual = parseResponse(mvcResult, TimerDescriptor.class);
    assertThat(actual).isEqualTo(timerDescriptor);
  }

  @Test
  void get_negative() throws Exception {
    var errorMessage = "timer not found by id: " + TIMER_UUID;
    when(schedulingTimerService.getById(TIMER_UUID)).thenThrow(new EntityNotFoundException(errorMessage));
    mockMvc.perform(get("/scheduler/timers/{id}", TIMER_UUID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("$.errors[0].code", is("not_found_error")));
  }

  @Test
  void get_negative_unsupportedError() throws Exception {
    when(schedulingTimerService.getById(TIMER_UUID)).thenThrow(new UnsupportedOperationException("unsupported"));

    mockMvc.perform(get("/scheduler/timers/{id}", TIMER_UUID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().is5xxServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("unsupported")))
      .andExpect(jsonPath("$.errors[0].type", is("UnsupportedOperationException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")));
  }

  @Test
  void get_all() throws Exception {
    var timerDescriptors = SearchResult.of(
      List.of(new TimerDescriptor().id(TIMER_UUID), new TimerDescriptor().id(randomUUID())));

    when(schedulingTimerService.getAll(0, 10)).thenReturn(timerDescriptors);
    var mvcResult = mockMvc.perform(get("/scheduler/timers")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    var actual = parseResponse(mvcResult, TimerDescriptorList.class);
    assertThat(actual).isEqualTo(new TimerDescriptorList()
      .timerDescriptors(timerDescriptors.getRecords())
      .totalRecords(timerDescriptors.getTotalRecords()));
  }

  @Test
  void create_positive() throws Exception {
    var timerDescriptor = timerDescriptor(null);
    when(schedulingTimerService.create(timerDescriptor)).thenReturn(timerDescriptor());

    var result = mockMvc.perform(post("/scheduler/timers")
        .contentType(APPLICATION_JSON)
        .content(asJsonString(timerDescriptor)))
      .andExpect(status().isCreated())
      .andReturn();

    var actual = parseResponse(result, TimerDescriptor.class);
    assertThat(actual).isEqualTo(timerDescriptor());
  }

  @Test
  void create_negative_internalServerError() throws Exception {
    var descriptor = timerDescriptor(null);
    when(schedulingTimerService.create(descriptor)).thenThrow(new RuntimeException("Unknown error"));
    mockMvc.perform(post("/scheduler/timers")
        .content(asJsonString(descriptor))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString("Unknown error")))
      .andExpect(jsonPath("$.errors[0].type", is("RuntimeException")))
      .andExpect(jsonPath("$.errors[0].code", is("unknown_error")));
  }

  @Test
  void create_negative_invalidRequestBody() throws Exception {
    var errorMsgSubstring = "JSON parse error: Unexpected character ('[' (code 91)): "
      + "was expecting double-quote to start field name";
    mockMvc.perform(post("/scheduler/timers")
        .content("{[..]")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString(errorMsgSubstring)))
      .andExpect(jsonPath("$.errors[0].type", is("HttpMessageNotReadableException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void update_positive() throws Exception {
    var timerDescriptor = timerDescriptor();
    when(schedulingTimerService.update(TIMER_UUID, timerDescriptor)).thenReturn(timerDescriptor);

    var mvcResult = mockMvc.perform(put("/scheduler/timers/{id}", TIMER_UUID)
        .contentType(APPLICATION_JSON)
        .content(asJsonString(timerDescriptor)))
      .andExpect(status().is2xxSuccessful())
      .andReturn();

    var actual = parseResponse(mvcResult, TimerDescriptor.class);
    assertThat(actual).isEqualTo(timerDescriptor);
  }

  @Test
  void update_negative_entityNotFound() throws Exception {
    var errorMsg = "timer not found by id: " + TIMER_UUID;
    var timerDescriptor = timerDescriptor();
    when(schedulingTimerService.update(TIMER_UUID, timerDescriptor)).thenThrow(new EntityNotFoundException(errorMsg));
    mockMvc.perform(put("/scheduler/timers/{id}", TIMER_UUID)
        .contentType(APPLICATION_JSON)
        .content(asJsonString(timerDescriptor)))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMsg)))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("$.errors[0].code", is("not_found_error")));
  }

  @Test
  void update_negative_idInRequestBodyNotMatch() throws Exception {
    var errorMessage = "Id in the url and in the entity must match";
    var timerDescriptor = timerDescriptor(randomUUID());
    when(schedulingTimerService.update(TIMER_UUID, timerDescriptor))
      .thenThrow(new RequestValidationException(errorMessage, "id", "null"));

    mockMvc.perform(put("/scheduler/timers/{id}", TIMER_UUID)
        .contentType(APPLICATION_JSON)
        .content(asJsonString(timerDescriptor)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void delete_positive() throws Exception {
    doNothing().when(schedulingTimerService).delete(TIMER_UUID);
    mockMvc.perform(delete("/scheduler/timers/{id}", TIMER_UUID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }
}
