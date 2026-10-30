/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_INVALID_RESPONSE_SCHEMA;

import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import java.io.IOException;
import java.io.OutputStream;
import org.jspecify.annotations.Nullable;

/** Bounds response schema serialization before provider-specific conversion. */
public class ResponseSchemaValidator {

  private final ObjectMapper objectMapper;
  private final long maxSizeBytes;
  private final int maxNestingDepth;

  public ResponseSchemaValidator(
      ObjectMapper objectMapper, long maxSizeBytes, int maxNestingDepth) {
    this.maxSizeBytes = maxSizeBytes;
    this.maxNestingDepth = maxNestingDepth;
    this.objectMapper = objectMapper.copy().disable(SerializationFeature.INDENT_OUTPUT);
    this.objectMapper
        .getFactory()
        .setStreamWriteConstraints(
            StreamWriteConstraints.builder().maxNestingDepth(maxNestingDepth).build());
  }

  public void validate(@Nullable ResponseFormatConfiguration format) {
    if (!(format instanceof JsonResponseFormatConfiguration json) || !json.hasSchema()) {
      return;
    }

    final var output =
        new OutputStream() {
          private long remaining = maxSizeBytes;

          @Override
          public void write(int value) throws IOException {
            if (remaining == 0) {
              throw new IOException("Response schema size limit exceeded");
            }
            remaining--;
          }

          @Override
          public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length > remaining) {
              throw new IOException("Response schema size limit exceeded");
            }
            remaining -= length;
          }
        };

    try {
      objectMapper.writeValue(output, json.schema());
    } catch (IOException e) {
      // Jackson's exception path can contain schema keys, so do not expose the cause.
      throw new ConnectorException(
          ERROR_CODE_INVALID_RESPONSE_SCHEMA,
          "Response JSON schema could not be serialized within the configured limits of %d bytes and nesting depth of %d"
              .formatted(maxSizeBytes, maxNestingDepth));
    }
  }
}
