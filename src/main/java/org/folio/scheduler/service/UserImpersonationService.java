package org.folio.scheduler.service;

public interface UserImpersonationService {

  String impersonate(String tenant, String userId);
}
