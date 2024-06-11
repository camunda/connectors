/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.base.client.apache.builder;

import io.camunda.connector.http.base.client.apache.builder.parts.ApacheRequestAuthenticationBuilder;
import io.camunda.connector.http.base.client.apache.builder.parts.ApacheRequestBodyBuilder;
import io.camunda.connector.http.base.client.apache.builder.parts.ApacheRequestHeadersBuilder;
import io.camunda.connector.http.base.client.apache.builder.parts.ApacheRequestPartBuilder;
import io.camunda.connector.http.base.client.apache.builder.parts.ApacheRequestQueryParametersBuilder;
import io.camunda.connector.http.base.client.apache.builder.parts.ApacheRequestUriBuilder;
import io.camunda.connector.http.base.model.HttpCommonRequest;
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

  public ClassicHttpRequest build(HttpCommonRequest request) {
    ClassicRequestBuilder requestBuilder = ClassicRequestBuilder.create(request.getMethod().name());
    for (ApacheRequestPartBuilder b : builders) {
      b.build(requestBuilder, request);
    }
    return requestBuilder.build();
  }
}
