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
package io.camunda.connector.runtime.core.intrinsic.functions;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class GetJsonFunctionTest {

  private final GetJsonFunction function = new GetJsonFunction();

  private final InMemoryDocumentStore store = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory factory = new DocumentFactoryImpl(store);

  @Test
  void shouldReturnWholeJsonIfNoExpression() {
    String json = "{\"foo\":123,\"bar\":\"baz\"}";
    var request = DocumentCreationRequest.from(json.getBytes()).build();
    Document doc = factory.create(request);
    Object result = function.execute(doc, null);
    assertThat(((java.util.Map<?, ?>) result).get("foo")).isEqualTo(123);
    assertThat(((java.util.Map<?, ?>) result).get("bar")).isEqualTo("baz");
  }

  @Test
  void shouldReturnPartOfJsonWithFeelExpression() {
    String json = "{\"foo\":123,\"bar\":\"baz\"}";
    var request = DocumentCreationRequest.from(json.getBytes()).build();
    Document doc = factory.create(request);
    Object result = function.execute(doc, "foo");
    assertThat(result).isEqualTo(123L);
  }

  @Test
  void shouldReturnPartOfJsonWithExpression_Object() {
    String json =
        """
        {
          "foo": "123",
          "bar": {
            "baz": "baz"
          }
        }
        """;
    var request = DocumentCreationRequest.from(json.getBytes()).build();
    Document doc = factory.create(request);
    Object result = function.execute(doc, "bar");

    assertThat(result).isEqualTo(Map.of("baz", "baz"));
  }
}
