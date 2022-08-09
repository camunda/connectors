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

package io.camunda.connector.gdrive;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentResponse;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.drive.model.File;
import com.google.gson.Gson;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.MimeTypeUrl;
import io.camunda.connector.gdrive.model.request.Resource;
import io.camunda.connector.gdrive.model.request.Template;
import io.camunda.connector.gdrive.model.request.Type;
import io.camunda.connector.gdrive.supliers.GsonComponentSupplier;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleDriveService {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveService.class);

  private final Gson gson;
  private final JsonFactory jsonFactory;

  public GoogleDriveService() {
    this(GsonComponentSupplier.getGson(), GsonComponentSupplier.getJsonFactory());
  }

  public GoogleDriveService(final Gson gson, final JsonFactory jsonFactory) {
    this.gson = gson;
    this.jsonFactory = jsonFactory;
  }

  public GoogleDriveResult execute(final GoogleDriveClient client, final Resource resource) {
    switch (resource.getType()) {
      case FOLDER:
        return createFolder(client, resource);
      case FILE:
        return createFile(client, resource);
      default:
        LOGGER.warn("Unsupported resource type : [{}]", resource.getType());
        throw new IllegalArgumentException("Unsupported resource type : " + resource.getType());
    }
  }

  private GoogleDriveResult createFolder(final GoogleDriveClient client, final Resource resource) {
    File fileMetadata = createMetaDataFile(resource);
    File result = client.createWithMetadata(fileMetadata);
    LOGGER.debug(
        "Folder successfully created, id: [{}] name: [{}]", result.getId(), resource.getName());
    return new GoogleDriveResult(
        result.getId(), MimeTypeUrl.getResourceUrl(result.getMimeType(), result.getId()));
  }

  private GoogleDriveResult createFile(final GoogleDriveClient client, final Resource resource) {
    final Optional<List<Request>> requestsForFile = getRequestsForFile(resource);
    final File metaData = createMetaDataFile(resource);
    File result;
    if (resource.getTemplate() != null) {
      result = client.createWithTemplate(metaData, resource.getTemplate().getId());
      LOGGER.debug(
          "File successfully created by template, file name [{}], templateId [{}]",
          result.getId(),
          resource.getName());
      requestsForFile.ifPresent(requests -> updateWithRequests(client, requests, result));
    } else {
      result = client.createWithMetadata(metaData);
    }
    return new GoogleDriveResult(
        result.getId(), MimeTypeUrl.getResourceUrl(result.getMimeType(), result.getId()));
  }

  private File createMetaDataFile(final Resource resource) {
    return Optional.ofNullable(resource.getAdditionalGoogleDriveProperties())
        .map(prop -> gson.fromJson(StringEscapeUtils.unescapeJson(prop), File.class))
        .orElseGet(File::new)
        .setName(resource.getName())
        .setMimeType(resource.getType() == Type.FOLDER ? MimeTypeUrl.FOLDER.getMimeType() : null)
        .setParents(resource.getParent() != null ? List.of(resource.getParent()) : null);
  }

  private Optional<List<Request>> getRequestsForFile(Resource resource) {
    return Optional.ofNullable(resource.getTemplate())
        .map(Template::getVariables)
        .map(StringEscapeUtils::unescapeJson)
        .map(
            variables -> {
              try (JsonParser jsonParser = jsonFactory.createJsonParser(variables)) {
                return List.copyOf(jsonParser.parseArrayAndClose(List.class, Request.class));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
  }

  private void updateWithRequests(
      final GoogleDriveClient client, final List<Request> requests, final File metaData) {
    if (MimeTypeUrl.DOCUMENT.getMimeType().equals(metaData.getMimeType())) {
      BatchUpdateDocumentResponse response = client.updateDocument(metaData.getId(), requests);
      LOGGER.debug("Variables successfully replaced for file id [{}]", response.getDocumentId());
    } else {
      LOGGER.warn(
          "It was not possible to update file id [{}] "
              + "this feature is not yet implemented for the type [{}]",
          metaData.getId(),
          metaData.getMimeType());
    }
  }
}
