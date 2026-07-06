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
package io.camunda.connector.runtime.core.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReturn;
import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.api.document.DocumentReturnFormat;
import io.camunda.connector.api.document.RawPayload;
import io.camunda.connector.api.error.ConnectorException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts a {@link DocumentReturn} value produced by a connector into the connector's final
 * response object. Takes the user-selected {@link DocumentReturnFormat} (read from the job's input
 * variables by the runtime), performs the conversion, and invokes the connector-supplied {@code
 * wrap} lambda.
 */
public class DocumentReturnProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(DocumentReturnProcessor.class);

  /**
   * Conservative guard for the {@code TEXT}/{@code JSON} branches. Sits below Zeebe's default 4 MiB
   * gRPC message limit so the rest of the variable envelope (response headers, metadata, gRPC
   * framing) still fits. Payloads above this size must use {@link DocumentReturnChoice#DOCUMENT}.
   */
  static final int MAX_INLINE_PAYLOAD_BYTES = 3 * 1024 * 1024;

  private final DocumentFactory documentFactory;
  private final ObjectMapper objectMapper;

  public DocumentReturnProcessor(DocumentFactory documentFactory, ObjectMapper objectMapper) {
    this.documentFactory = documentFactory;
    this.objectMapper = objectMapper;
  }

  public Object process(DocumentReturn<?> documentReturn, DocumentReturnFormat responseFormat) {
    if (responseFormat == null || responseFormat.choice() == null) {
      throw new ConnectorException(
          "Connector returned DocumentReturn but the runtime could not read a"
              + " documentReturnFormat.choice from the job's input variables. Make sure the"
              + " element template exposes the @DocumentReturnFormat dropdown and that"
              + " 'documentReturnFormat' is listed in the connector's inputVariables.");
    }
    Charset encoding = resolveEncoding(responseFormat.encoding());
    LOG.debug(
        "Processing DocumentReturn: choice={}, fileName={}, contentType={}, encoding={}",
        responseFormat.choice(),
        documentReturn.payload().fileName(),
        documentReturn.payload().contentType(),
        responseFormat.choice() == DocumentReturnChoice.TEXT ? encoding.name() : "n/a");
    Object converted = convert(documentReturn.payload(), responseFormat.choice(), encoding);
    return documentReturn.wrap().apply(converted, responseFormat.choice());
  }

  private Object convert(RawPayload payload, DocumentReturnChoice choice, Charset encoding) {
    try (InputStream stream = payload.stream()) {
      return switch (choice) {
        case DOCUMENT -> {
          var doc =
              documentFactory.create(
                  DocumentCreationRequest.from(stream)
                      .contentType(payload.contentType())
                      .fileName(payload.fileName())
                      .build());
          LOG.debug(
              "Uploaded document for response: reference={}, fileName={}",
              doc.reference(),
              payload.fileName());
          yield doc;
        }
        case TEXT -> {
          byte[] bytes = stream.readAllBytes();
          ensureFitsInVariable(bytes.length, "Text");
          var text = new String(bytes, encoding);
          LOG.debug("Decoded text payload: chars={}, encoding={}", text.length(), encoding.name());
          yield text;
        }
        case JSON -> {
          byte[] bytes = stream.readAllBytes();
          ensureFitsInVariable(bytes.length, "JSON");
          var json = parseJson(bytes);
          LOG.debug(
              "Parsed JSON payload: rootType={}",
              json == null ? "null" : json.getClass().getSimpleName());
          yield json;
        }
      };
    } catch (IOException e) {
      throw new ConnectorException(
          null, "Failed to read payload for document response conversion: " + e.getMessage(), e);
    }
  }

  private Object parseJson(byte[] bytes) throws IOException {
    try {
      JsonNode node = objectMapper.readTree(bytes);
      if (node == null || node.isMissingNode()) {
        throw new ConnectorException(
            "JSON response format selected but payload was empty or could not be parsed.");
      }
      return objectMapper.treeToValue(node, Object.class);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          null,
          "JSON response format selected but payload is not valid JSON: " + e.getMessage(),
          e);
    }
  }

  private static Charset resolveEncoding(String encoding) {
    if (encoding == null || encoding.isBlank()) {
      return StandardCharsets.UTF_8;
    }
    try {
      return Charset.forName(encoding);
    } catch (IllegalArgumentException e) {
      throw new ConnectorException(
          null,
          "Unsupported charset '"
              + encoding
              + "' configured on documentReturnFormat.encoding. Use a valid IANA charset name (e.g."
              + " UTF-8, ISO-8859-1) or leave the field blank to default to UTF-8.",
          e);
    }
  }

  private static void ensureFitsInVariable(int byteLength, String formatLabel) {
    if (byteLength <= MAX_INLINE_PAYLOAD_BYTES) {
      return;
    }
    double payloadMiB = byteLength / (1024.0 * 1024.0);
    int limitMiB = MAX_INLINE_PAYLOAD_BYTES / (1024 * 1024);
    throw new ConnectorException(
        String.format(
            "%s response payload is %.1f MiB, which exceeds the inline variable size limit of %d"
                + " MiB. Re-run with 'Document reference' as the response format so the payload"
                + " is uploaded to the document store instead.",
            formatLabel, payloadMiB, limitMiB));
  }
}
