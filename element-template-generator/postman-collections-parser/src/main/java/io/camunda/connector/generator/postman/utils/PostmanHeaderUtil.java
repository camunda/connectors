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

import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class PostmanHeaderUtil {

  public static Set<HttpOperationProperty> transformToHeaderProperty(Endpoint endpoint) {
    Set<HttpOperationProperty> headerParams = new HashSet<>();
    endpoint
        .request()
        .headers()
        .forEach(
            h ->
                headerParams.add(
                    HttpOperationProperty.createStringProperty(
                        h.key(),
                        Target.HEADER,
                        "",
                        h.disabled(),
                        Optional.ofNullable(h.value())
                            .orElse("")
                            .replace("{{", "")
                            .replace("}}", ""))));
    return headerParams;
  }
}
