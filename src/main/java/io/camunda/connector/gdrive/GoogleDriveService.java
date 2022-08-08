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

import com.google.api.services.drive.model.File;
import com.google.gson.Gson;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.request.Resource;
import io.camunda.connector.gdrive.model.request.Type;
import io.camunda.connector.gdrive.supliers.GJsonComponentSupplier;
import java.util.List;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleDriveService {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveService.class);
  private static final String FOLDER_URL_TEMPLATE = "https://drive.google.com/drive/folders/%s";
  protected static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

  private final Gson gson;

  public GoogleDriveService() {
    this.gson = GJsonComponentSupplier.getGson();
  }

  public GoogleDriveService(final Gson gson) {
    this.gson = gson;
  }

  public GoogleDriveResult execute(final GoogleDriveClient client, final Resource resource) {
    File fileMetadata = createMetaDataFile(resource);
    File result = client.createWithMetadata(fileMetadata);
    LOGGER.debug(
        "Folder successfully created, id: [{}] name: [{}]", result.getId(), resource.getName());
    return new GoogleDriveResult(result.getId(), getFolderUrlById(result.getId()));
  }

  private File createMetaDataFile(final Resource resource) {
    File fileMetadata;
    String properties = resource.getAdditionalGoogleDriveProperties();
    if (properties != null && !properties.isBlank()) {
      fileMetadata = gson.fromJson(StringEscapeUtils.unescapeJson(properties), File.class);
    } else {
      fileMetadata = new File();
    }

    fileMetadata.setName(resource.getName());
    if (resource.getType() == Type.FOLDER) {
      fileMetadata.setMimeType(FOLDER_MIME_TYPE);
    }
    if (resource.getParent() != null) {
      fileMetadata.setParents(List.of(resource.getParent()));
    }
    return fileMetadata;
  }

  protected String getFolderUrlById(final String folderId) {
    return String.format(FOLDER_URL_TEMPLATE, folderId);
  }
}
