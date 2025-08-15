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
package io.camunda.connector.http.client.client.apache.builder.parts;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.http.client.cloudfunction.CloudFunctionFilePart;
import io.camunda.connector.http.client.cloudfunction.CloudFunctionService;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A builder for creating a multipart entity from a map. The map represents the parts of a
 * multipart/form-data request. The keys are the names of the parts and the values are the content
 * of the parts.
 *
 * <p>See {@link DocumentAwareMultipartEntityBuilder#createMultiPartEntity(Map)} for more details.
 */
public class DocumentAwareMultipartEntityBuilder {

  private static final Logger LOG =
      LoggerFactory.getLogger(DocumentAwareMultipartEntityBuilder.class);
  private final ContentType contentType;
  private final Map<?, ?> body;
  private final MultipartEntityBuilder builder;

  public DocumentAwareMultipartEntityBuilder(Map<?, ?> body, ContentType contentType) {
    this.body = body;
    this.contentType = contentType;
    this.builder = MultipartEntityBuilder.create();
  }

  public HttpEntity build() {
    builder.setMode(HttpMultipartMode.LEGACY);
    Optional.ofNullable(contentType.getParameter("boundary")).ifPresent(builder::setBoundary);

    return createMultiPartEntity(body);
  }

  /**
   * Creates a multipart entity from the given body. A multipart/form-data request is represented by
   * a map where the keys are the names of the parts and the values are the content of the parts.
   * The content (map values) can be:
   *
   * <ul>
   *   <li>Maps that are convertible to {@link CloudFunctionFilePart}. If the map is not
   *       convertible, an error is logged and the map is ignored.
   *   <li>Documents
   *   <li>Lists that contain Documents or Maps that are convertible to {@link
   *       CloudFunctionFilePart}. If the map is not convertible, an error is logged and the list is
   *       ignored.
   *   <li>Null values are ignored
   *   <li>Any other value is converted to a text body
   * </ul>
   *
   * @param body the multipart body
   * @return the multipart entity
   */
  private HttpEntity createMultiPartEntity(Map<?, ?> body) {
    for (Map.Entry<?, ?> entry : body.entrySet()) {
      switch (entry.getValue()) {
        case Map map -> handleFilePartContent(map);
        case Document document -> handleDocumentContent(entry, document);
        case List<?> list -> handleListContent(entry, list);
        case null -> {}
        default ->
            builder.addTextBody(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
      }
    }
    return builder.build();
  }

  /**
   * Converts a map to a {@link CloudFunctionFilePart} and adds it to the multipart entity builder.
   * Map values that are Maps are expected to be convertible to CloudFunctionFilePart in the context
   * of a multipart/form-data request executed in SaaS.
   *
   * @see CloudFunctionService
   */
  private void handleFilePartContent(Map map) {
    try {
      var part =
          ConnectorsObjectMapperSupplier.getCopy().convertValue(map, CloudFunctionFilePart.class);
      builder.addBinaryBody(
          part.name(),
          new ByteArrayInputStream(part.content()),
          getContentType(part.contentType()),
          part.fileName());
    } catch (IllegalArgumentException | NullPointerException e) {
      LOG.error(
          "Failed to convert the provided map {} to CloudFunctionFilePart. A request with Content-Type: multipart/form-data can only contain maps that are convertible to CloudFunctionFilePart.",
          map,
          e);
    }
  }

  private void handleListContent(Map.Entry<?, ?> entry, List<?> list) {
    for (Object item : list) {
      switch (item) {
        case Document document -> handleDocumentContent(entry, document);
        case Map map -> handleFilePartContent(map);
        default -> builder.addTextBody(String.valueOf(entry.getKey()), String.valueOf(item));
      }
    }
  }

  private void handleDocumentContent(Map.Entry<?, ?> entry, Document document) {
    DocumentMetadata metadata = document.metadata();
    builder.addBinaryBody(
        String.valueOf(entry.getKey()),
        document.asInputStream(),
        getContentType(metadata.getContentType()),
        metadata.getFileName());
  }

  private ContentType getContentType(String rawContentType) {
    ContentType contentType;
    try {
      contentType = ContentType.create(rawContentType);
    } catch (IllegalArgumentException e) {
      contentType = ContentType.DEFAULT_BINARY;
    }
    return contentType;
  }
}
