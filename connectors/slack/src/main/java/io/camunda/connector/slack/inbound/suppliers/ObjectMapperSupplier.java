/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound.suppliers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.scala.DefaultScalaModule$;

public final class ObjectMapperSupplier {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new Jdk8Module())
      .registerModule(DefaultScalaModule$.MODULE$)
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private ObjectMapperSupplier() {}

  public static ObjectMapper getMapperInstance() {
    return MAPPER;
  }
}
