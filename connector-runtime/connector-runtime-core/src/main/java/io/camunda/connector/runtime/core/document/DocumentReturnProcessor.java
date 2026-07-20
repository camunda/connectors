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
import io.camunda.connector.api.document.InlineSizeGuard;
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
    LOG.debug(
        "Processing DocumentReturn: choice={}, fileName={}, contentType={}",
        responseFormat.choice(),
        documentReturn.payload().fileName(),
        documentReturn.payload().contentType());
    Object converted =
        convert(documentReturn.payload(), responseFormat.choice(), responseFormat.encoding());
    return documentReturn.wrap().apply(converted, responseFormat.choice());
  }

  private Object convert(RawPayload payload, DocumentReturnChoice choice, String encodingName) {
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
          // Encoding is only relevant for TEXT; resolving it here means an invalid encoding left
          // over from a previous TEXT selection can't fail an otherwise valid DOCUMENT/JSON
          // download.
          Charset encoding = resolveEncoding(encodingName);
          byte[] bytes = readBounded(stream);
          ensureFitsInVariable(bytes.length, "Text");
          var text = new String(bytes, encoding);
          LOG.debug("Decoded text payload: chars={}, encoding={}", text.length(), encoding.name());
          yield text;
        }
        case JSON -> {
          byte[] bytes = readBounded(stream);
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

  /**
   * Reads at most {@link InlineSizeGuard#MAX_INLINE_BYTES} + 1 bytes from the payload stream. The
   * one extra byte lets {@link #ensureFitsInVariable} detect an over-limit payload without
   * buffering the whole remote object into heap first — a multi-GB download decoded as TEXT/JSON
   * would otherwise exhaust the runtime heap before the size-limit incident could be raised.
   */
  private static byte[] readBounded(InputStream stream) throws IOException {
    return stream.readNBytes((int) (InlineSizeGuard.MAX_INLINE_BYTES + 1));
  }

  private static void ensureFitsInVariable(int byteLength, String formatLabel) {
    if (byteLength <= InlineSizeGuard.MAX_INLINE_BYTES) {
      return;
    }
    // The read is capped at MAX_INLINE_BYTES + 1, so the exact payload size is unknown here — the
    // message reports the limit rather than a misleading truncated size.
    double limitMiB = InlineSizeGuard.MAX_INLINE_BYTES / (1024.0 * 1024.0);
    throw new ConnectorException(
        String.format(
            "%s response payload exceeds the inline variable size limit of %.1f MiB. Re-run with"
                + " 'Document reference' as the response format so the payload is uploaded to the"
                + " document store instead.",
            formatLabel, limitMiB));
  }
}
