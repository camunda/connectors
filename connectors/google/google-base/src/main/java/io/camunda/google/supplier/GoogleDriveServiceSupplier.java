/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *             under one or more contributor license agreements. Licensed under a proprietary license.
 *             See the License.txt file for more information. You may not use this file
 *             except in compliance with the proprietary license.
 */
package io.camunda.google.supplier;

import static io.camunda.google.supplier.util.GoogleServiceSupplierUtil.getHttpHttpCredentialsAdapter;
import static io.camunda.google.supplier.util.GoogleServiceSupplierUtil.getNetHttpTransport;

import com.google.api.services.drive.Drive;
import io.camunda.google.model.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleDriveServiceSupplier {

  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveServiceSupplier.class);

  private GoogleDriveServiceSupplier() {}

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
}
