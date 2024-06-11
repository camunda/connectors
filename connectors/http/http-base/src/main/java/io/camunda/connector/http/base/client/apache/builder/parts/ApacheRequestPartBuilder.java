/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.client.apache.builder.parts;

import io.camunda.connector.http.base.model.HttpCommonRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

/**
 * Maps a part of a {@link HttpCommonRequest} to an Apache {@link ClassicRequestBuilder}. Each part
 * of the request is mapped by a different builder, for example the request body, the URI, the
 * authentication, etc.
 */
public interface ApacheRequestPartBuilder {

  void build(ClassicRequestBuilder builder, HttpCommonRequest request);
}
