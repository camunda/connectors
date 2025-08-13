/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.utils;

import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthentication;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthenticationType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcsUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(GcsUtil.class);

  public static String uploadNewFileFromDocument(
      final Document document,
      final String fileName,
      final String bucketName,
      final String projectId,
      GcpAuthentication authentication)
      throws IOException {
    LOGGER.debug("Starting document upload to Google Cloud Storage");
    Storage storage =
        StorageOptions.newBuilder()
            .setProjectId(projectId)
            .setCredentials(getCredentials(authentication))
            .build()
            .getService();

    BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, fileName).build();
    storage.createFrom(blobInfo, document.asInputStream());
    return String.format("gs://%s/%s", bucketName, fileName);
  }

  public static void deleteObjectFromBucket(
      final String bucketName,
      final String objectName,
      final String projectId,
      final GcpAuthentication authentication) {

    LOGGER.debug("Starting object deletion from Google Cloud Storage");
    Storage storage =
        StorageOptions.newBuilder()
            .setProjectId(projectId)
            .setCredentials(getCredentials(authentication))
            .build()
            .getService();

    boolean deleted = storage.delete(bucketName, objectName);
    if (deleted) {
      LOGGER.info("Object {} was deleted from bucket {}", objectName, bucketName);
    } else {
      LOGGER.warn("Object {} was not found in bucket {}", objectName, bucketName);
    }
  }

  public static Credentials getCredentials(GcpAuthentication auth) {
    if (auth.authType() == GcpAuthenticationType.BEARER) {
      AccessToken accessToken = new AccessToken(auth.bearerToken(), null);
      return GoogleCredentials.create(accessToken);
    } else if (auth.authType() == GcpAuthenticationType.SERVICE_ACCOUNT) {
      ByteArrayInputStream credentialsStream =
          new ByteArrayInputStream(auth.serviceAccountJson().getBytes(StandardCharsets.UTF_8));
      try {
        return GoogleCredentials.fromStream(credentialsStream);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return UserCredentials.newBuilder()
        .setClientId(auth.oauthClientId())
        .setClientSecret(auth.oauthClientSecret())
        .setRefreshToken(auth.oauthRefreshToken())
        .build();
  }
}
