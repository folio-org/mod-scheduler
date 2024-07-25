package org.folio.scheduler.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperSupplier;

public class HibernateObjectMapperSupplier implements ObjectMapperSupplier {

  @Override
  public ObjectMapper get() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }
}
