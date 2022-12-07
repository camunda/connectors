/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.supliers;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.drive.Drive;
import com.google.auth.http.HttpCredentialsAdapter;
import io.camunda.connector.gdrive.model.request.Authentication;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GoogleServicesSupplier {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleServicesSupplier.class);

  private GoogleServicesSupplier() {}

  public static Drive createDriveClientInstance(final Authentication authentication) {
    Drive drive =
        new Drive.Builder(
                getNetHttpTransport(),
                GsonComponentSupplier.gsonFactoryInstance(),
                getHttpHttpCredentialsAdapter(authentication))
            .build();
    LOGGER.debug("Google drive service was successfully initialized");
    return drive;
  }

  public static Docs createDocsClientInstance(final Authentication auth) {
    Docs docs =
        new Docs.Builder(
                getNetHttpTransport(),
                GsonComponentSupplier.gsonFactoryInstance(),
                getHttpHttpCredentialsAdapter(auth))
            .build();
    LOGGER.debug("Google docs service was successfully initialized");
    return docs;
  }

  private static HttpCredentialsAdapter getHttpHttpCredentialsAdapter(final Authentication auth) {
    return new HttpCredentialsAdapter(auth.fetchCredentials());
  }

  private static NetHttpTransport getNetHttpTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("An error occurred while creating the HTTP_TRANSPORT", e);
    }
  }
}
