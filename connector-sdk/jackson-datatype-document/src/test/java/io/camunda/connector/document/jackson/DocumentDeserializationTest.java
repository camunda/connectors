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
package io.camunda.connector.document.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.camunda.connector.document.annotation.jackson.DocumentReferenceModel;
import io.camunda.connector.document.annotation.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.annotation.jackson.JacksonModuleDocument;
import io.camunda.document.Document;
import io.camunda.document.DocumentFactory;
import io.camunda.document.operation.DocumentOperationExecutor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DocumentDeserializationTest {

  @Mock private DocumentFactory factory;
  @Mock private DocumentOperationExecutor operationExecutor;

  private ObjectMapper objectMapper;

  public record TargetTypeDocument(Document document) {}

  @BeforeEach
  public void initialize() {
    objectMapper =
        new ObjectMapper()
            .registerModule(new JacksonModuleDocument(factory, operationExecutor))
            .registerModule(new Jdk8Module());
  }

  @Test
  void targetTypeDocument() {
    var ref = createDocumentMock("Hello World");
    var payload = Map.of("document", ref);
    var result = objectMapper.convertValue(payload, TargetTypeDocument.class);
    assertEquals(ref, result.document.reference());
  }

  public record TargetTypeByteArray(byte[] document) {}

  @Test
  void targetTypeByteArray() {
    var contentString = "Hello World";
    var ref = createDocumentMock(contentString);
    var payload = Map.of("document", ref);
    var result = objectMapper.convertValue(payload, TargetTypeByteArray.class);
    assertEquals(contentString, new String(result.document));
  }

  public record TargetTypeInputStream(InputStream document) {}

  @Test
  void targetTypeInputStream() throws IOException {
    var contentString = "Hello World";
    var ref = createDocumentMock(contentString);
    var payload = Map.of("document", ref);
    var result = objectMapper.convertValue(payload, TargetTypeInputStream.class);
    assertEquals(contentString, new String(result.document.readAllBytes()));
  }

  public record TargetTypeObject(Object document) {}

  @Test
  void targetTypeObject() {
    var ref = createDocumentMock("Hello World");
    var payload = Map.of("document", ref);
    var result = objectMapper.convertValue(payload, TargetTypeObject.class);
    assertInstanceOf(Document.class, result.document);
    assertEquals(ref, ((Document) result.document).reference());
  }

  @Test
  @SuppressWarnings("unchecked")
  void targetTypeObject_NestedObject() {
    var ref = createDocumentMock("Hello World");
    var payload = Map.of("document", Map.of("document", ref));
    var result = objectMapper.convertValue(payload, TargetTypeObject.class);
    assertInstanceOf(Map.class, result.document);
    var nested = (Map<String, Object>) result.document;
    assertInstanceOf(Document.class, nested.get("document"));
    assertEquals(ref, ((Document) nested.get("document")).reference());
  }

  @Test
  @SuppressWarnings("unchecked")
  void targetTypeObject_Array() {
    var ref = createDocumentMock("Hello World");
    var payload = Map.of("document", List.of(ref));
    var result = objectMapper.convertValue(payload, TargetTypeObject.class);
    Assertions.assertInstanceOf(List.class, result.document);
    var list = (List<Object>) result.document;
    assertInstanceOf(Document.class, list.get(0));
    assertEquals(ref, ((Document) list.get(0)).reference());
  }

  private DocumentReferenceModel createDocumentMock(String content) {
    var ref =
        new CamundaDocumentReferenceModel(
            "default", UUID.randomUUID().toString(), Map.of(), Optional.empty());
    Document document = mock(Document.class);
    lenient().when(document.asByteArray()).thenReturn(content.getBytes());
    lenient().when(document.reference()).thenReturn(ref);
    lenient()
        .when(document.asInputStream())
        .thenReturn(new ByteArrayInputStream(content.getBytes()));
    when((factory.resolve(ref))).thenReturn(document);
    return ref;
  }
}
