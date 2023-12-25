package org.folio.scheduler.integration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.net.URI;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "okapi")
public interface OkapiClient {

  /**
   * Performs GET HTTP Request.
   *
   * @param uri - uniform resource identifier
   */
  @GetMapping(consumes = APPLICATION_JSON_VALUE)
  void doGet(URI uri);

  /**
   * Performs POST HTTP Request.
   *
   * @param uri - uniform resource identifier
   */
  @PostMapping(consumes = APPLICATION_JSON_VALUE)
  void doPost(URI uri);

  /**
   * Performs PUT HTTP Request.
   *
   * @param uri - uniform resource identifier
   */
  @PutMapping(consumes = APPLICATION_JSON_VALUE)
  void doPut(URI uri);

  /**
   * Performs DELETE HTTP Request.
   *
   * @param uri - uniform resource identifier
   */
  @DeleteMapping(consumes = APPLICATION_JSON_VALUE)
  void doDelete(URI uri);
}
