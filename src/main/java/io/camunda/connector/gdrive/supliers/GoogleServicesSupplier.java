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

package io.camunda.connector.gdrive.supliers;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GoogleServicesSupplier {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleServicesSupplier.class);

  private GoogleServicesSupplier() {}

  public static Drive createDriveClientInstance(final String token, final JsonFactory jsonFactory) {
    Drive drive =
        new Drive.Builder(getNetHttpTransport(), jsonFactory, getHttpHttpCredentialsAdapter(token))
            .build();
    LOGGER.debug("Google drive service was successfully initialized");
    return drive;
  }

  public static Docs createDocsClientInstance(final String token, final JsonFactory jsonFactory) {
    Docs docs =
        new Docs.Builder(getNetHttpTransport(), jsonFactory, getHttpHttpCredentialsAdapter(token))
            .build();
    LOGGER.debug("Google docs service was successfully initialized");
    return docs;
  }

  private static HttpCredentialsAdapter getHttpHttpCredentialsAdapter(final String token) {
    return new HttpCredentialsAdapter(createGoogleCredentials(token));
  }

  private static GoogleCredentials createGoogleCredentials(final String token) {
    AccessToken accessToken = new AccessToken(token, null);
    return new GoogleCredentials(accessToken).createScoped(DriveScopes.DRIVE);
  }

  private static NetHttpTransport getNetHttpTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("An error occurred while creating the HTTP_TRANSPORT", e);
    }
  }
}
