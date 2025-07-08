/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.response;

public record UploadResponse(String bucket, String filename, String downloadUrl) {
  public UploadResponse(String bucket, String filename) {
    this(bucket, filename, getDownloadUrl(bucket, filename));
  }

  private static String getDownloadUrl(String bucket, String filename) {
    return "https://storage.cloud.google.com/" + bucket + "/" + filename;
  }
}
