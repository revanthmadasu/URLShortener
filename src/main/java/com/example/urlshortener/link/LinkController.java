package com.example.urlshortener.link;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.link.LinkService.CreateResult;
import com.example.urlshortener.link.dto.CreateLinkRequest;
import com.example.urlshortener.link.dto.CreateLinkResponse;
import com.example.urlshortener.link.dto.LinkResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Management API for links. Redirection itself is served by {@link RedirectController}. */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

  private final LinkService linkService;
  private final String baseUrl;

  public LinkController(LinkService linkService, AppProperties properties) {
    this.linkService = linkService;
    this.baseUrl = properties.baseUrl();
  }

  @PostMapping
  public ResponseEntity<CreateLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
    CreateResult result = linkService.create(request);
    CreateLinkResponse body =
        CreateLinkResponse.of(result.link(), baseUrl, result.managementToken());
    URI location = URI.create("/api/v1/links/" + result.link().getShortCode());
    return ResponseEntity.created(location).body(body);
  }

  @GetMapping("/{code}")
  public LinkResponse get(@PathVariable String code) {
    return LinkResponse.from(linkService.getByCode(code), baseUrl);
  }

  @DeleteMapping("/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable String code,
      @RequestHeader(name = "X-Management-Token", required = false) String managementToken) {
    linkService.delete(code, managementToken);
  }
}
