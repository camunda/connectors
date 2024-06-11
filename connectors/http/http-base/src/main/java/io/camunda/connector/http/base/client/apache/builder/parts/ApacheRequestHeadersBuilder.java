/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.client.apache.builder.parts;

import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

import io.camunda.connector.http.base.model.HttpCommonRequest;
import java.util.Optional;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

public class ApacheRequestHeadersBuilder implements ApacheRequestPartBuilder {
  @Override
  public void build(ClassicRequestBuilder builder, HttpCommonRequest request) {
    var hasContentTypeHeader =
        Optional.ofNullable(request.getHeaders())
            .map(headers -> headers.containsKey(CONTENT_TYPE))
            .orElse(false);
    if (request.getMethod().supportsBody && !hasContentTypeHeader) {
      // default content type
      builder.addHeader(CONTENT_TYPE, APPLICATION_JSON.getMimeType());
    }
    if (request.getHeaders() == null) {
      request.setHeaders(new java.util.HashMap<>());
    }
    request.getHeaders().forEach(builder::addHeader);
  }
}
