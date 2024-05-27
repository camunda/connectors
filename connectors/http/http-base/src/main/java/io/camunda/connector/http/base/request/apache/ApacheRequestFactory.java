/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.request.apache;

import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.request.RequestFactory;
import io.camunda.connector.http.base.request.apache.bulider.ApacheRequestBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;

/**
 * Maps a {@link HttpCommonRequest}(element template model) to an Apache {@link ClassicHttpRequest}.
 */
public class ApacheRequestFactory implements RequestFactory<ClassicHttpRequest> {
  private static final ApacheRequestFactory INSTANCE = new ApacheRequestFactory();

  private ApacheRequestFactory() {}

  public static ApacheRequestFactory get() {
    return INSTANCE;
  }

  @Override
  public ClassicHttpRequest createHttpRequest(HttpCommonRequest request) throws Exception {
    return ApacheRequestBuilder.create().build(request);
  }
}
