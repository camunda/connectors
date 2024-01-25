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

import static io.camunda.connector.generator.postman.utils.PostmanOperationUtil.POSTMAN_VARIABLE_PREFIX_TAG;
import static io.camunda.connector.generator.postman.utils.PostmanOperationUtil.POSTMAN_VARIABLE_SUFFIX_TAG;

import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class PostmanPathUtil {

  public static Set<HttpOperationProperty> transformToPathProperty(Endpoint endpoint) {
    Set<HttpOperationProperty> pathParams = new HashSet<>();
    var urlWithoutQueryParams = extractPathFromUrl(endpoint);

    // variables {variable}
    var pathArguments =
        Optional.ofNullable(StringUtils.substringsBetween(urlWithoutQueryParams, "{", "}"))
            .orElse(new String[] {});

    for (String pathArgument : pathArguments) {
      pathParams.add(
          HttpOperationProperty.createStringProperty(pathArgument, Target.PATH, "", true, ""));
    }
    return pathParams;
  }

  public static String extractPathFromUrl(Endpoint endpoint) {
    List<String> pathSegments = new ArrayList<>();

    if (StringUtils.isNotBlank(endpoint.request().url().protocol())) {
      pathSegments.add(
          endpoint.request().url().protocol() + ":/"); // FIXME: one slash from protocol is weird
    }

    var host = String.join(".", endpoint.request().url().host());
    pathSegments.add(host);

    pathSegments.addAll(endpoint.request().url().path());
    return pathSegments.stream()
        .map(s -> s.replace("+", "_"))
        .map(
            s -> {
              if (s.startsWith(POSTMAN_VARIABLE_PREFIX_TAG)
                  && s.endsWith(POSTMAN_VARIABLE_SUFFIX_TAG)) {
                return s.replace(POSTMAN_VARIABLE_PREFIX_TAG, "{")
                    .replace(POSTMAN_VARIABLE_SUFFIX_TAG, "}");
              }
              return s;
            })
        .map(
            s -> {
              if (s.startsWith(":")) {
                return s.replace(":", "{").concat("}");
              }
              return s;
            })
        .collect(Collectors.joining("/", "", ""));
  }
}
