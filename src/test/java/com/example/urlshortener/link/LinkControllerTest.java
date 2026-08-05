package com.example.urlshortener.link;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.common.error.Errors;
import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.link.LinkService.CreateResult;
import com.example.urlshortener.support.TestFixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LinkController.class)
@Import(LinkControllerTest.Config.class)
class LinkControllerTest {

  static class Config {
    @Bean
    AppProperties appProperties() {
      return TestFixtures.appProperties();
    }
  }

  @Autowired private MockMvc mockMvc;
  @MockitoBean private LinkService linkService;

  private static Link sampleLink() {
    return Link.create(
        "abc1234", "https://example.com", "hash", Instant.parse("2026-08-05T00:00:00Z"), null);
  }

  @Test
  void createReturns201WithLocationAndToken() throws Exception {
    when(linkService.create(any())).thenReturn(new CreateResult(sampleLink(), "tok-123"));

    mockMvc
        .perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/links/abc1234"))
        .andExpect(jsonPath("$.link.shortCode").value("abc1234"))
        .andExpect(jsonPath("$.link.shortUrl").value("http://localhost:8080/abc1234"))
        .andExpect(jsonPath("$.managementToken").value("tok-123"));
  }

  @Test
  void createWithBlankUrlReturns400ProblemJson() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", "application/problem+json"))
        .andExpect(jsonPath("$.errors[0].field").value("url"));
  }

  @Test
  void getReturnsMetadata() throws Exception {
    when(linkService.getByCode("abc1234")).thenReturn(sampleLink());

    mockMvc
        .perform(get("/api/v1/links/abc1234"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shortCode").value("abc1234"))
        .andExpect(jsonPath("$.longUrl").value("https://example.com"));
  }

  @Test
  void getUnknownReturns404ProblemJson() throws Exception {
    when(linkService.getByCode("missing")).thenThrow(new Errors.NotFound("nope"));

    mockMvc
        .perform(get("/api/v1/links/missing"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", "application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://errors.urlshortener.example/link-not-found"));
  }

  @Test
  void deleteReturns204AndForwardsToken() throws Exception {
    mockMvc
        .perform(delete("/api/v1/links/abc1234").header("X-Management-Token", "tok-123"))
        .andExpect(status().isNoContent());

    verify(linkService).delete("abc1234", "tok-123");
  }

  @Test
  void deleteWithBadTokenReturns403() throws Exception {
    doThrow(new Errors.Forbidden("bad token")).when(linkService).delete(eq("abc1234"), any());

    mockMvc
        .perform(delete("/api/v1/links/abc1234").header("X-Management-Token", "wrong"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("https://errors.urlshortener.example/forbidden"));
  }
}
