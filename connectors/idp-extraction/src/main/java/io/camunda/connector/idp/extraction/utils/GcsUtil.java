/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.utils;

import static io.camunda.google.supplier.util.GoogleServiceSupplierUtil.getCredentials;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.camunda.document.Document;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcsUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(GcsUtil.class);

  public static String uploadNewFileFromDocument(
      final Document document,
      final String bucketName,
      final String projectId,
      Authentication authentication)
      throws IOException {
    LOGGER.debug("Starting document upload to Google Cloud Storage");
    Storage storage =
        StorageOptions.newBuilder()
            .setProjectId(projectId)
            .setCredentials(getCredentials(authentication))
            .build()
            .getService();

    BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, document.metadata().getFileName()).build();
    storage.createFrom(blobInfo, document.asInputStream());
    return String.format("gs://%s/%s", bucketName, document.metadata().getFileName());
  }

  public static void deleteObjectFromBucket(
      final String bucketName,
      final String objectName,
      final String projectId,
      final Authentication authentication) {

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
}
