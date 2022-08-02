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

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import io.camunda.connector.gdrive.model.request.Authentication;
import io.camunda.connector.gdrive.model.request.FolderCreateParams;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleDriveClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveClient.class);

  private static final String NAME = "name";
  private static final String ID = "id";
  private static final String PARENTS = "parents";

  private static final String ERROR_CREATING = "An error occurred while creating the %s";
  private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

  private final JsonFactory jsonFactory;
  private Drive driveService;

  public GoogleDriveClient() {
    this.jsonFactory = GsonComponentSupplier.getJsonFactory();
  }

  public GoogleDriveClient(final Drive driveService, final JsonFactory jsonFactory) {
    this.driveService = driveService;
    this.jsonFactory = jsonFactory;
  }

  public void init(final Authentication authentication) {
    GoogleCredentials googleCredentials = createGoogleCredentials(authentication);
    HttpCredentialsAdapter httpCredentialsAdapter = new HttpCredentialsAdapter(googleCredentials);
    driveService = createDriveService(authentication.getApplicationName(), httpCredentialsAdapter);
    LOGGER.debug(
        "Google client was successfully initialized, ApplicationName: [{}]",
        authentication.getApplicationName());
  }

  private GoogleCredentials createGoogleCredentials(final Authentication authentication) {
    AccessToken token = new AccessToken(authentication.getToken(), null);
    return new GoogleCredentials(token).createScoped(DriveScopes.DRIVE);
  }

  private Drive createDriveService(
      final String appName, final HttpCredentialsAdapter httpCredentialsAdapter) {
    return new Drive.Builder(getNetHttpTransport(), jsonFactory, httpCredentialsAdapter)
        .setApplicationName(appName)
        .build();
  }

  private NetHttpTransport getNetHttpTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException(String.format(ERROR_CREATING, "HTTP_TRANSPORT"), e);
    }
  }

  public File createMetaData(final FolderCreateParams folder) {
    File fileMetadata = new File();
    fileMetadata.setName(folder.getName());
    fileMetadata.setMimeType(FOLDER_MIME_TYPE);
    if (folder.getParent() != null) {
      fileMetadata.setParents(Collections.singletonList(folder.getParent()));
    }
    if (folder.getAdditionalProperties() != null) {
      fileMetadata.setDescription(folder.getAdditionalProperties());
    }
    return fileMetadata;
  }

  public File createFolder(final File fileMetadata) {
    try {
      return driveService
          .files()
          .create(fileMetadata)
          .setFields(NAME)
          .setFields(PARENTS)
          .setFields(ID)
          .execute();
    } catch (IOException e) {
      throw new RuntimeException(String.format(ERROR_CREATING, "file"), e);
    }
  }

  public void shutdown() {
    driveService = null;
  }

  protected Drive getDriveService() {
    return driveService;
  }
}
