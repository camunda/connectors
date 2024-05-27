/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.request.apache.bulider.parts;

import io.camunda.connector.http.base.model.HttpCommonRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

public class ApacheRequestUriBuilder implements ApacheRequestPartBuilder {

  @Override
  public void build(ClassicRequestBuilder builder, HttpCommonRequest request) throws Exception {
    builder.setUri(request.getUrl());
  }
}
