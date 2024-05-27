/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.request.apache.bulider;

import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.request.apache.bulider.parts.ApacheRequestAuthenticationBuilder;
import io.camunda.connector.http.base.request.apache.bulider.parts.ApacheRequestBodyBuilder;
import io.camunda.connector.http.base.request.apache.bulider.parts.ApacheRequestHeadersBuilder;
import io.camunda.connector.http.base.request.apache.bulider.parts.ApacheRequestPartBuilder;
import io.camunda.connector.http.base.request.apache.bulider.parts.ApacheRequestQueryParametersBuilder;
import io.camunda.connector.http.base.request.apache.bulider.parts.ApacheRequestUriBuilder;
import java.util.ArrayList;
import java.util.List;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

/**
 * Builder for Apache {@link ClassicHttpRequest}.
 *
 * <p>Follows the composite builder pattern to create a {@link ClassicHttpRequest} from a {@link
 * HttpCommonRequest}. The builder is composed of multiple {@link ApacheRequestPartBuilder}s that
 * are responsible for building different parts of the request.
 */
public class ApacheRequestBuilder {
  private final List<ApacheRequestPartBuilder> builders = new ArrayList<>();

  ApacheRequestBuilder(List<ApacheRequestPartBuilder> builders) {
    this.builders.addAll(builders);
  }

  /**
   * Creates a new instance of the builder. The default builders are:
   *
   * <ul>
   *   <li>{@link ApacheRequestBodyBuilder}
   *   <li>{@link ApacheRequestUriBuilder}
   *   <li>{@link ApacheRequestAuthenticationBuilder}
   *   <li>{@link ApacheRequestHeadersBuilder}
   *   <li>{@link ApacheRequestQueryParametersBuilder}
   * </ul>
   *
   * @return a new instance of the builder
   */
  public static ApacheRequestBuilder create() {
    return new ApacheRequestBuilder(
        List.of(
            new ApacheRequestBodyBuilder(),
            new ApacheRequestUriBuilder(),
            new ApacheRequestAuthenticationBuilder(),
            new ApacheRequestHeadersBuilder(),
            new ApacheRequestQueryParametersBuilder()));
  }

  public ClassicHttpRequest build(HttpCommonRequest request) throws Exception {
    ClassicRequestBuilder requestBuilder = ClassicRequestBuilder.create(request.getMethod().name());
    for (ApacheRequestPartBuilder b : builders) {
      b.build(requestBuilder, request);
    }
    return requestBuilder.build();
  }
}
