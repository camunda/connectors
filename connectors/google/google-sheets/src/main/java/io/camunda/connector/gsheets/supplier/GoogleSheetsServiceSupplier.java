/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.supplier;

import static io.camunda.google.supplier.util.GoogleServiceSupplierUtil.getHttpHttpCredentialsAdapter;
import static io.camunda.google.supplier.util.GoogleServiceSupplierUtil.getNetHttpTransport;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import io.camunda.google.model.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GoogleSheetsServiceSupplier {

  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleSheetsServiceSupplier.class);

  private GoogleSheetsServiceSupplier() {}

  public static Sheets getGoogleSheetsService(final Authentication auth) {
    Sheets sheets =
        new Sheets.Builder(
                getNetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                getHttpHttpCredentialsAdapter(auth))
            .build();

    LOGGER.debug("Google sheets service was successfully initialized");
    return sheets;
  }
}
