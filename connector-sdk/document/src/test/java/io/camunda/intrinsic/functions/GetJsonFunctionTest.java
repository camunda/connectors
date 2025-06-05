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
package io.camunda.intrinsic.functions;

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.document.Document;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.factory.DocumentFactoryImpl;
import io.camunda.document.store.DocumentCreationRequest;
import io.camunda.document.store.InMemoryDocumentStore;
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
    assertEquals(123, ((java.util.Map<?, ?>) result).get("foo"));
    assertEquals("baz", ((java.util.Map<?, ?>) result).get("bar"));
  }

  @Test
  void shouldReturnPartOfJsonWithFeelExpression() {
    String json = "{\"foo\":123,\"bar\":\"baz\"}";
    var request = DocumentCreationRequest.from(json.getBytes()).build();
    Document doc = factory.create(request);
    Object result = function.execute(doc, "foo");
    assertEquals(123L, result);
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

    assertEquals(Map.of("baz", "baz"), result);
  }
}
