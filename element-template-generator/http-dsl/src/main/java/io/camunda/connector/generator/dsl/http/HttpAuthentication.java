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
package io.camunda.connector.generator.dsl.http;

import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyConstraints;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.http.base.auth.BasicAuthentication;
import io.camunda.connector.http.base.auth.BearerAuthentication;
import io.camunda.connector.http.base.auth.NoAuthentication;
import io.camunda.connector.http.base.auth.OAuthAuthentication;
import java.util.List;
import java.util.Set;

public interface HttpAuthentication {

  /** Human-readable id */
  String label();

  /** Connector-readable ID */
  String id();

  static List<PropertyBuilder> getPropertyPrefabs(HttpAuthentication auth) {
    if (auth instanceof OAuth2) {
      return List.of(
          HiddenProperty.builder()
              .id("authentication.type")
              .value(OAuthAuthentication.TYPE)
              .group("authentication")
              .binding(new ZeebeInput("authentication.type")),
          StringProperty.builder()
              .id("authentication.oauthTokenEndpoint")
              .label("Oauth token endpoint")
              .description("The OAuth token endpoint")
              .optional(false)
              .constraints(PropertyConstraints.builder().notEmpty(true).build())
              .feel(FeelMode.optional)
              .group("authentication")
              .binding(new ZeebeInput("authentication.oauthTokenEndpoint")),
          StringProperty.builder()
              .id("authentication.clientId")
              .label("Client id")
              .description("Your application's client ID from the OAuth client")
              .optional(false)
              .constraints(PropertyConstraints.builder().notEmpty(true).build())
              .feel(FeelMode.optional)
              .group("authentication")
              .binding(new ZeebeInput("authentication.clientId")),
          StringProperty.builder()
              .id("authentication.clientSecret")
              .label("Client secret")
              .description("Your application's client secret from the OAuth client")
              .optional(false)
              .constraints(PropertyConstraints.builder().notEmpty(true).build())
              .feel(FeelMode.optional)
              .group("authentication")
              .binding(new ZeebeInput("authentication.clientSecret")),
          StringProperty.builder()
              .id("authentication.audience")
              .label("Audience")
              .description("The unique identifier of the target API you want to access (optional)")
              .optional(true)
              .feel(FeelMode.optional)
              .group("authentication")
              .binding(new ZeebeInput("authentication.audience")));
    }
    if (auth instanceof BasicAuth) {
      return List.of(
          HiddenProperty.builder()
              .id("authentication.type")
              .value(BasicAuthentication.TYPE)
              .group("authentication")
              .binding(new ZeebeInput("authentication.type")),
          StringProperty.builder()
              .id("authentication.username")
              .label("Username")
              .optional(false)
              .constraints(PropertyConstraints.builder().notEmpty(true).build())
              .feel(FeelMode.optional)
              .group("authentication")
              .binding(new ZeebeInput("authentication.username")),
          StringProperty.builder()
              .id("authentication.password")
              .label("Password")
              .optional(false)
              .constraints(PropertyConstraints.builder().notEmpty(true).build())
              .feel(FeelMode.optional)
              .group("authentication")
              .binding(new ZeebeInput("authentication.password")));
    }
    if (auth instanceof BearerAuth) {
      return List.of(
          HiddenProperty.builder()
              .id("authentication.type")
              .value(BearerAuthentication.TYPE)
              .group("authentication")
              .binding(new ZeebeInput("authentication.type")),
          StringProperty.builder()
              .id("authentication.token")
              .label("Bearer token")
              .optional(false)
              .constraints(PropertyConstraints.builder().notEmpty(true).build())
              .feel(FeelMode.optional)
              .group("authentication")
              .binding(new ZeebeInput("authentication.token")));
    }
    if (auth instanceof NoAuth) {
      return List.of(
          HiddenProperty.builder()
              .id("authentication.type")
              .value(NoAuthentication.TYPE)
              .group("authentication")
              .binding(new ZeebeInput("authentication.type")));
    }
    throw new IllegalArgumentException("Unknown authentication type: " + auth);
  }

  class NoAuth implements HttpAuthentication {

    public static final NoAuth INSTANCE = new NoAuth();

    @Override
    public String label() {
      return "None";
    }

    @Override
    public String id() {
      return NoAuthentication.TYPE;
    }
  }

  class BasicAuth implements HttpAuthentication {

    public static final BasicAuth INSTANCE = new BasicAuth();

    @Override
    public String label() {
      return "Basic";
    }

    @Override
    public String id() {
      return BasicAuthentication.TYPE;
    }
  }

  class BearerAuth implements HttpAuthentication {

    public static final BearerAuth INSTANCE = new BearerAuth();

    @Override
    public String label() {
      return "Bearer token";
    }

    @Override
    public String id() {
      return BearerAuthentication.TYPE;
    }
  }

  record OAuth2(String tokenUrl, Set<String> scopes) implements HttpAuthentication {

    @Override
    public String label() {
      return "OAuth 2.0";
    }

    @Override
    public String id() {
      return OAuthAuthentication.TYPE;
    }
  }
}
