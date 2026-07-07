/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Jackson serializer counterpart to {@link FeelOffsetDateTimeDeserializer}; writes the plain offset
 * form so it round-trips through {@link FeelOffsetDateTimeParser#parse(String)}.
 */
public class FeelOffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {
  @Override
  public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeString(FeelOffsetDateTimeParser.format(value));
  }
}
