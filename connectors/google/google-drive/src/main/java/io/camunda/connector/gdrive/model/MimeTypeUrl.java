/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *             under one or more contributor license agreements. Licensed under a proprietary license.
 *             See the License.txt file for more information. You may not use this file
 *             except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model;

import java.util.Map;
import java.util.Optional;

public enum MimeTypeUrl {
  FOLDER("application/vnd.google-apps.folder", "https://drive.google.com/drive/folders/%s"),
  DOCUMENT("application/vnd.google-apps.document", "https://docs.google.com/document/d/%s"),
  SPREADSHEET(
      "application/vnd.google-apps.spreadsheet", "https://docs.google.com/spreadsheets/d/%s"),
  PRESENTATION(
      "application/vnd.google-apps.presentation", "https://docs.google.com/presentation/d/%s"),
  FORM("application/vnd.google-apps.form", "https://docs.google.com/forms/d/%s");

  private String mimeType;
  private String templateUrl;

  private static Map<String, String> values =
      Map.of(
          FOLDER.mimeType, FOLDER.templateUrl,
          DOCUMENT.mimeType, DOCUMENT.templateUrl,
          SPREADSHEET.mimeType, SPREADSHEET.templateUrl,
          PRESENTATION.mimeType, PRESENTATION.templateUrl,
          FORM.mimeType, FORM.templateUrl);

  MimeTypeUrl(final String mimeType, final String templateUrl) {
    this.mimeType = mimeType;
    this.templateUrl = templateUrl;
  }

  public static String getResourceUrl(final String mimeType, final String id) {
    return Optional.ofNullable(mimeType)
        .map(type -> values.get(type))
        .map(type -> String.format(type, id))
        .orElse(null);
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(final String mimeType) {
    this.mimeType = mimeType;
  }

  public String getTemplateUrl() {
    return templateUrl;
  }

  public void setTemplateUrl(final String templateUrl) {
    this.templateUrl = templateUrl;
  }

  public static Map<String, String> getValues() {
    return values;
  }

  public static void setValues(final Map<String, String> values) {
    MimeTypeUrl.values = values;
  }
}
