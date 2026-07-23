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
package io.camunda.connector.api.document;

import java.io.InputStream;
import java.util.function.BiFunction;

/**
 * Return value that lets the runtime convert a {@link RawPayload} into a {@link Document}, {@link
 * String}, or parsed JSON tree according to the {@link DocumentReturnChoice} the user picked on the
 * BPMN side, then wraps that converted value into the connector's own response shape via the {@code
 * wrap} lambda.
 *
 * <p>The runtime reads the user's choice and (for {@code TEXT}) the encoding from the job's input
 * variables via {@link
 * io.camunda.connector.api.outbound.OutboundConnectorContext#readDocumentReturnFormat()} — the
 * connector doesn't carry these values on its request POJO.
 *
 * @param payload the bytes to convert
 * @param wrap lambda receiving the converted value (a {@link Document}, {@link String}, or parsed
 *     JSON tree) and the user's choice, returning the connector-specific response object
 * @param <T> the connector-specific response type produced by {@code wrap}
 */
public record DocumentReturn<T>(
    RawPayload payload, BiFunction<Object, DocumentReturnChoice, T> wrap) {

  /**
   * Convenience factory that builds the {@link RawPayload} from a stream, so connectors don't have
   * to construct it themselves.
   */
  public static <T> DocumentReturn<T> of(
      InputStream stream,
      String contentType,
      String fileName,
      BiFunction<Object, DocumentReturnChoice, T> wrap) {
    return new DocumentReturn<>(new RawPayload(stream, contentType, fileName), wrap);
  }

  /**
   * Convenience factory that builds the {@link RawPayload} from a byte array, so connectors don't
   * have to construct it themselves.
   */
  public static <T> DocumentReturn<T> of(
      byte[] bytes,
      String contentType,
      String fileName,
      BiFunction<Object, DocumentReturnChoice, T> wrap) {
    return new DocumentReturn<>(RawPayload.of(bytes, contentType, fileName), wrap);
  }
}
