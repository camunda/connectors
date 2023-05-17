/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *             under one or more contributor license agreements. Licensed under a proprietary license.
 *             See the License.txt file for more information. You may not use this file
 *             except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.supliers;

import static io.camunda.google.supplier.util.GoogleServiceSupplierUtil.getHttpHttpCredentialsAdapter;
import static io.camunda.google.supplier.util.GoogleServiceSupplierUtil.getNetHttpTransport;

import com.google.api.services.docs.v1.Docs;
import io.camunda.google.model.Authentication;
import io.camunda.google.supplier.GsonComponentSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GoogleDocsServiceSupplier {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDocsServiceSupplier.class);

  private GoogleDocsServiceSupplier() {}

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
}
