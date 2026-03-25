package org.folio.scheduler.integration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.net.URI;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange(url = "okapi", contentType = APPLICATION_JSON_VALUE)
public interface OkapiClient {

  String MODULE_HINT = "x-okapi-module-hint";

  /**
   * Performs GET HTTP Request.
   *
   * @param uri - uniform resource identifier
   */
  @GetExchange
  void doGet(URI uri, @RequestHeader(MODULE_HINT) String moduleHint);

  /**
   * Performs POST HTTP Request.
   *
   * @param uri - uniform resource identifier
   */
  @PostExchange
  void doPost(URI uri, @RequestHeader(MODULE_HINT) String moduleHint);

  /**
   * Performs PUT HTTP Request.
   *
   * @param uri - uniform resource identifier
   */
  @PutExchange
  void doPut(URI uri, @RequestHeader(MODULE_HINT) String moduleHint);

  /**
   * Performs DELETE HTTP Request.
   *
   * @param uri - uniform resource identifier
   */
  @DeleteExchange
  void doDelete(URI uri, @RequestHeader(MODULE_HINT) String moduleHint);
}
