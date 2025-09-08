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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.IntrinsicFunctionExecutor;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DocumentDeserializationTest {

  private final CamundaDocumentStore documentStoreMock = mock(CamundaDocumentStore.class);
  private final DocumentFactory factory = new DocumentFactoryImpl(documentStoreMock);
  private final IntrinsicFunctionExecutor operationExecutor = mock(IntrinsicFunctionExecutor.class);

  private ObjectMapper objectMapper;

  @BeforeEach
  public void initialize() {
    objectMapper =
        new ObjectMapper()
            .registerModule(new JacksonModuleDocumentDeserializer(factory, operationExecutor))
            .registerModule(new Jdk8Module());
  }

  @Test
  void targetTypeDocument() {
    var ref = createDocumentMock("Hello World", null, documentStoreMock);
    var payload = Map.of("document", ref);
    var result = objectMapper.convertValue(payload, TargetTypeDocument.class);
    assertEquals(ref, result.document.reference());
  }

  @Test
  void targetTypeByteArray() {
    var contentString = "Hello World";
    var ref = createDocumentMock(contentString, null, documentStoreMock);
    var payload = Map.of("document", ref);
    var result = objectMapper.convertValue(payload, TargetTypeByteArray.class);
    assertEquals(contentString, new String(result.document));
  }

  @Test
  void targetTypeInputStream() throws IOException {
    var contentString = "Hello World";
    var ref = createDocumentMock(contentString, null, documentStoreMock);
    var payload = Map.of("document", ref);
    var result = objectMapper.convertValue(payload, TargetTypeInputStream.class);
    assertEquals(contentString, new String(result.document.readAllBytes()));
  }

  @Test
  void targetTypeObject() {
    var ref = createDocumentMock("Hello World", null, documentStoreMock);
    var payload = Map.of("document", ref);
    var result = objectMapper.convertValue(payload, TargetTypeObject.class);
    assertInstanceOf(Document.class, result.document);
    assertEquals(ref, ((Document) result.document).reference());
  }

  @Test
  @SuppressWarnings("unchecked")
  void targetTypeObject_NestedObject() {
    var ref = createDocumentMock("Hello World", null, documentStoreMock);
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
    var ref = createDocumentMock("Hello World", null, documentStoreMock);
    var payload = Map.of("document", List.of(ref));
    var result = objectMapper.convertValue(payload, TargetTypeObject.class);
    Assertions.assertInstanceOf(List.class, result.document);
    var list = (List<Object>) result.document;
    assertInstanceOf(Document.class, list.get(0));
    assertEquals(ref, ((Document) list.get(0)).reference());
  }

  @Test
  void targetTypeString() {
    var contentString = "Hello World";
    var ref = createDocumentMock(contentString, null, documentStoreMock);
    var payload = Map.of("document", ref);
    var result = objectMapper.convertValue(payload, TargetTypeString.class);
    assertEquals(Base64.getEncoder().encodeToString(contentString.getBytes()), result.document);
  }

  public static DocumentReferenceModel createDocumentMock(
      String content,
      CamundaDocumentMetadataModel metadata,
      CamundaDocumentStore documentStoreMock) {
    return createDocumentMock(content.getBytes(), metadata, documentStoreMock);
  }

  public static DocumentReferenceModel createDocumentMock(
      byte[] content,
      CamundaDocumentMetadataModel metadata,
      CamundaDocumentStore documentStoreMock) {
    var ref =
        new CamundaDocumentReferenceModel(
            "default", UUID.randomUUID().toString(), "hash", metadata);
    lenient()
        .when((documentStoreMock.getDocumentContent(ref)))
        .thenReturn(new ByteArrayInputStream(content));
    return ref;
  }

  public record TargetTypeDocument(Document document) {}

  public record TargetTypeByteArray(byte[] document) {}

  public record TargetTypeInputStream(InputStream document) {}

  public record TargetTypeObject(Object document) {}

  public record TargetTypeString(String document) {}
}
