/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Jackson deserializer for {@link OffsetDateTime} fields that may be populated from a FEEL {@code
 * now()} expression; see {@link FeelOffsetDateTimeParser} for why this is needed instead of
 * Jackson's default {@code JavaTimeModule} handling.
 */
public class FeelOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {
  @Override
  public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    return FeelOffsetDateTimeParser.parse(p.getValueAsString());
  }
}
