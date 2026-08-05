package com.example.urlshortener.analytics;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.analytics.dto.ClickStatsResponse;
import com.example.urlshortener.common.error.Errors;
import com.example.urlshortener.config.WebConfig;
import com.example.urlshortener.link.LinkService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = StatsController.class,
    excludeFilters =
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class))
class StatsControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private LinkService linkService;
  @MockitoBean private ClickAnalyticsService analytics;

  @Test
  void returnsStatsForKnownCode() throws Exception {
    when(analytics.stats(eq("abc1234"), eq(7)))
        .thenReturn(new ClickStatsResponse("abc1234", 7, 100L, 40L, List.of(), List.of()));

    mockMvc
        .perform(get("/api/v1/links/abc1234/stats").param("days", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalClicks").value(100))
        .andExpect(jsonPath("$.uniqueVisitors").value(40))
        .andExpect(jsonPath("$.windowDays").value(7));

    verify(linkService).getByCode("abc1234"); // existence check performed
  }

  @Test
  void returns404WhenCodeUnknown() throws Exception {
    when(linkService.getByCode("missing")).thenThrow(new Errors.NotFound("nope"));

    mockMvc.perform(get("/api/v1/links/missing/stats")).andExpect(status().isNotFound());
  }

  @Test
  void rejectsInvalidDaysParam() throws Exception {
    mockMvc
        .perform(get("/api/v1/links/abc1234/stats").param("days", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void defaultsDaysWhenAbsent() throws Exception {
    when(analytics.stats(eq("abc1234"), eq(30)))
        .thenReturn(new ClickStatsResponse("abc1234", 30, 0L, 0L, List.of(), List.of()));

    mockMvc.perform(get("/api/v1/links/abc1234/stats")).andExpect(status().isOk());
    verify(analytics).stats("abc1234", 30);
  }
}
