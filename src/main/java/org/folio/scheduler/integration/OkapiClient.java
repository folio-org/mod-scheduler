package org.folio.scheduler.integration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.net.URI;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "okapi")
public interface OkapiClient {

  String MODULE_HINT = "x-okapi-module-hint";

  /**
   * Performs GET HTTP Request.
   *
   * @param uri - uniform resource identifier
   */
  @GetMapping(consumes = APPLICATION_JSON_VALUE)
  void doGet(URI uri, @RequestHeader(MODULE_HINT) String moduleHint);

  /**
   * Performs POST HTTP Request.
   *
   * @param uri - uniform resource identifier
   */
  @PostMapping(consumes = APPLICATION_JSON_VALUE)
  void doPost(URI uri, @RequestHeader(MODULE_HINT) String moduleHint);

  /**
   * Performs PUT HTTP Request.
   *
   * @param uri - uniform resource identifier
   */
  @PutMapping(consumes = APPLICATION_JSON_VALUE)
  void doPut(URI uri, @RequestHeader(MODULE_HINT) String moduleHint);

  /**
   * Performs DELETE HTTP Request.
   *
   * @param uri - uniform resource identifier
   */
  @DeleteMapping(consumes = APPLICATION_JSON_VALUE)
  void doDelete(URI uri, @RequestHeader(MODULE_HINT) String moduleHint);
}
