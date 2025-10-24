/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.client.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.utils.EnvVarHelper;
import io.camunda.connector.http.client.utils.JsonHelper;
import java.io.IOException;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;

/**
 * Common {@link ResponseMapper} implementations.
 *
 * <p>The provided mappers read the entire response body into memory, so they should only be used
 * when the response size is known to be small or bounded. For large responses, consider processing
 * the {@link StreamingHttpResponse} body stream directly.
 */
public final class ResponseMappers {

  public static ResponseMapper<String> asString() {
    return ResponseMappers::asString;
  }

  public static ResponseMapper<byte[]> asByteArray() {
    return ResponseMappers::asBytes;
  }

  public static ResponseMapper<JsonNode> asJsonNode(Supplier<ObjectMapper> objectMapperSupplier) {
    return (response) -> asJson(response, objectMapperSupplier.get());
  }

  public static <T> ResponseMapper<T> asJsonObject(
      Supplier<ObjectMapper> objectMapperSupplier, Class<T> valueType) {
    return (response) -> {
      var jsonNode = asJson(response, objectMapperSupplier.get());
      try {
        return objectMapperSupplier.get().treeToValue(jsonNode, valueType);
      } catch (JsonProcessingException e) {
        throw new ConnectorException(
            "Failed to map JSON to type " + valueType.getSimpleName() + ": " + jsonNode, e);
      }
    };
  }

  public static ResponseMapper<Void> asVoid() {
    return (response) -> null;
  }

  /**
   * The maximum size of the response body to read into memory, in bytes. If the response body
   * exceeds this size, an exception will be thrown.
   */
  private static final int MAX_IN_MEMORY_BODY_SIZE = EnvVarHelper.getMaxInMemoryBodySize();

  private static final String ERROR_MESSAGE_TOO_LARGE =
      "Response body exceeds maximum in-memory size of " + MAX_IN_MEMORY_BODY_SIZE + " bytes";

  private static String asString(StreamingHttpResponse response) {
    try {
      var bytes = response.body().readNBytes(MAX_IN_MEMORY_BODY_SIZE);
      if (response.body().read() != -1) {
        throw new RuntimeException(ERROR_MESSAGE_TOO_LARGE);
      }
      if (bytes.length == 0) {
        return null;
      }
      return new String(bytes);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read response body as string", e);
    }
  }

  private static byte[] asBytes(StreamingHttpResponse response) {
    try {
      var body = response.body().readNBytes(MAX_IN_MEMORY_BODY_SIZE);
      if (response.body().read() != -1) {
        throw new RuntimeException(ERROR_MESSAGE_TOO_LARGE);
      }
      return body;
    } catch (IOException e) {
      throw new RuntimeException("Failed to read response body as bytes", e);
    }
  }

  private static JsonNode asJson(StreamingHttpResponse response, ObjectMapper objectMapper) {
    var stringBody = asString(response);
    if (!JsonHelper.isJsonStringValid(stringBody)) {
      throw new ConnectorException(
          "Response body is not valid JSON: "
              + (StringUtils.isNotBlank(stringBody)
                  ? StringUtils.abbreviate(stringBody, 100)
                  : "<empty>"));
    }
    try {
      return objectMapper.readTree(stringBody);
    } catch (JsonProcessingException e) {
      throw new ConnectorException("Failed to parse JSON string: " + stringBody, e);
    }
  }
}
