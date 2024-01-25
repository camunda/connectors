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
package io.camunda.connector.generator.postman.utils;

import io.camunda.connector.generator.dsl.http.HttpAuthentication;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.ApiKey;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.BasicAuth;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.BearerAuth;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.NoAuth;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.OAuth2;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Auth.AuthEntry;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Auth.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SecurityUtils {

  public static List<HttpAuthentication> setGlobalAuthentication(
      PostmanCollectionV210 postmanCollection) {
    if (postmanCollection.auth() == null) {
      return Collections.emptyList();
    }

    if (postmanCollection.auth().type() == Type.basic) {
      return Arrays.asList(new BasicAuth());
    }

    if (postmanCollection.auth().type() == Type.bearer) {
      return Arrays.asList(new BearerAuth());
    }

    if (postmanCollection.auth().type() == Type.oauth2) {
      return Arrays.asList(new OAuth2("https://<auth-server>/token", Set.of("scope_1, scope_2")));
    }

    if (postmanCollection.auth().type() == Type.apikey) {
      var in = "headers";
      var key = "";
      var value = "";
      for (AuthEntry entry : postmanCollection.auth().apikey()) {
        if ("in".equalsIgnoreCase(entry.key())) {
          in = entry.value();
        } else if ("key".equalsIgnoreCase(entry.key())) {
          key = entry.value();
        } else if ("value".equalsIgnoreCase(entry.key())) {
          value = Optional.ofNullable(entry.value()).orElse("").replace("{{", "").replace("}}", "");
        }
      }
      return Arrays.asList(new ApiKey(in, key, value));
    }

    return Arrays.asList(new NoAuth());
  }
}
