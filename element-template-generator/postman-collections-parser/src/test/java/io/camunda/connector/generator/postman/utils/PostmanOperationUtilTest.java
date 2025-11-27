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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostmanOperationUtilTest {

  @Test
  void extractOperations_propertyOrderIsDeterministic() throws IOException {
    // given - parse a collection from test resources
    PostmanCollectionV210 collection;
    try (var input = new FileInputStream("src/test/resources/postman-books.json")) {
      collection = ObjectMapperProvider.getInstance().readValue(input, PostmanCollectionV210.class);
    }

    // when - extract operations 10 times to verify determinism
    List<List<String>> allPropertyOrders = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      var operations = PostmanOperationUtil.extractOperations(collection, Set.of());
      // Pick an operation that has properties (e.g., "add book" which has body properties)
      var addBookOperation =
          operations.stream().filter(op -> op.id().contains("add_book")).findFirst().orElseThrow();
      var props = addBookOperation.builder().getProperties();
      allPropertyOrders.add(toPropertyIdList(props));
    }

    // then - all extractions should produce the same order
    assertThat(allPropertyOrders).isNotEmpty();
    List<String> firstOrder = allPropertyOrders.get(0);
    for (List<String> order : allPropertyOrders) {
      assertThat(order)
          .as("Property order should be deterministic across multiple extractions")
          .isEqualTo(firstOrder);
    }
  }

  private List<String> toPropertyIdList(List<HttpOperationProperty> properties) {
    List<String> ids = new ArrayList<>();
    for (HttpOperationProperty prop : properties) {
      ids.add(prop.id());
    }
    return ids;
  }
}
