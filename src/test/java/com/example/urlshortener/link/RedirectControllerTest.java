package com.example.urlshortener.link;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.analytics.ClickAnalyticsService;
import com.example.urlshortener.common.error.Errors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private LinkService linkService;
  @MockitoBean private ClickAnalyticsService analytics;

  @Test
  void redirectsWith302AndLocation() throws Exception {
    when(linkService.resolveTargetUrl("abc1234")).thenReturn("https://example.com/page");

    mockMvc
        .perform(get("/abc1234"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/page"))
        .andExpect(header().string("Cache-Control", "private, no-cache, max-age=0"));

    // A successful redirect is a click and must be captured.
    verify(analytics).recordAsync(any());
  }

  @Test
  void unknownCodeReturns404() throws Exception {
    when(linkService.resolveTargetUrl("missing")).thenThrow(new Errors.NotFound("nope"));

    mockMvc.perform(get("/missing")).andExpect(status().isNotFound());
    verifyNoInteractions(analytics); // 404 is not a click
  }

  @Test
  void expiredLinkReturns410() throws Exception {
    when(linkService.resolveTargetUrl("expired")).thenThrow(new Errors.Gone("expired"));

    mockMvc.perform(get("/expired")).andExpect(status().isGone());
    verifyNoInteractions(analytics); // 410 is not a click
  }
}
