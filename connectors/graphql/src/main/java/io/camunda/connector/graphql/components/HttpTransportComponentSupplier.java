/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.components;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.json.JsonObjectParser;

public class HttpTransportComponentSupplier {

  private HttpTransportComponentSupplier() {}

  private static final HttpTransport HTTP_TRANSPORT = new ApacheHttpTransport();
  private static final HttpRequestFactory REQUEST_FACTORY =
      HTTP_TRANSPORT.createRequestFactory(
          request ->
              request.setParser(new JsonObjectParser(GsonComponentSupplier.gsonFactoryInstance())));

  public static HttpRequestFactory httpRequestFactoryInstance() {
    return REQUEST_FACTORY;
  }
}
