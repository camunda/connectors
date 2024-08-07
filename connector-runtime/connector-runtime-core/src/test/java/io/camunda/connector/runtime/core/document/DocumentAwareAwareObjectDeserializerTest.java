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
package io.camunda.connector.runtime.core.document;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentOperation;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.jackson.DocumentAware;
import io.camunda.connector.runtime.core.document.jackson.JacksonModuleDocument;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class DocumentAwareAwareObjectDeserializerTest {

  private final DocumentStore store = new InMemoryDocumentStore();
  private final DocumentFactory factory = new DocumentFactory(store);

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JacksonModuleDocument(factory))
          .registerModule(new Jdk8Module());

  public record TargetTypeDocument(Document document) {}

  @Test
  void noOperation_targetTypeDocument() {
    var ref = new CamundaDocumentReference("test", "test", Map.of(), Optional.empty());
    var payload = Map.of("document", ref);
    var result = objectMapper.convertValue(payload, TargetTypeDocument.class);
    assertEquals(ref, result.document.reference());
  }

  public record TargetTypeByteArray(@DocumentAware byte[] document) {}

  @Test
  void noOperation_targetTypeByteArray() {
    var contentString = "Hello World";
    var ref =
        store.createDocument(
            new DocumentMetadata(Map.of("Hello", "World")), contentString.getBytes());
    var payload = Map.of("document", ref);
    var result = objectMapper.convertValue(payload, TargetTypeByteArray.class);
    assertEquals(contentString, new String(result.document));
  }

  public record TargetTypeObject(@DocumentAware Object document) {}

  @Test
  void withOperation_targetTypeObject_operationPreserved() {
    var ref =
        new CamundaDocumentReference(
            "test", "test", Map.of(), Optional.of(new DocumentOperation("base64", Map.of())));
    var payload = Map.of("document", ref);
    var result = objectMapper.convertValue(payload, TargetTypeObject.class);
    assertEquals(ref, result.document);
  }
}
