/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.google.supplier.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.http.HttpCredentialsAdapter;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import java.security.GeneralSecurityException;

public final class GoogleServiceSupplierUtil {

  private GoogleServiceSupplierUtil() {}

  public static HttpCredentialsAdapter getHttpHttpCredentialsAdapter(final Authentication auth) {
    return new HttpCredentialsAdapter(auth.fetchCredentials());
  }

  public static NetHttpTransport getNetHttpTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("An error occurred while creating the HTTP_TRANSPORT", e);
    }
  }
}
