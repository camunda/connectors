/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model;

import java.util.Objects;

public class GoogleDriveResult {

  private String googleDriveResourceId;
  private String googleDriveResourceUrl;

  public GoogleDriveResult() {}

  public GoogleDriveResult(
      final String googleDriveResourceId, final String googleDriveResourceUrl) {
    this.googleDriveResourceId = googleDriveResourceId;
    this.googleDriveResourceUrl = googleDriveResourceUrl;
  }

  public String getGoogleDriveResourceId() {
    return googleDriveResourceId;
  }

  public void setGoogleDriveResourceId(final String googleDriveResourceId) {
    this.googleDriveResourceId = googleDriveResourceId;
  }

  public String getGoogleDriveResourceUrl() {
    return googleDriveResourceUrl;
  }

  public void setGoogleDriveResourceUrl(final String googleDriveResourceUrl) {
    this.googleDriveResourceUrl = googleDriveResourceUrl;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GoogleDriveResult result = (GoogleDriveResult) o;
    return Objects.equals(googleDriveResourceId, result.googleDriveResourceId)
        && Objects.equals(googleDriveResourceUrl, result.googleDriveResourceUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(googleDriveResourceId, googleDriveResourceUrl);
  }

  @Override
  public String toString() {
    return "GoogleDriveResult{"
        + "googleDriveResourceId='"
        + googleDriveResourceId
        + "'"
        + ", googleDriveResourceUrl='"
        + googleDriveResourceUrl
        + "'"
        + "}";
  }
}
