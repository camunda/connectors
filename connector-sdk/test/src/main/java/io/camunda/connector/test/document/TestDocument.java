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
package io.camunda.connector.test.document;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

public class TestDocument implements Document {

  private final byte[] content;
  private final DocumentMetadata metadata;
  private final DocumentReference reference;
  private final String documentId;

  public TestDocument(
      byte[] content, DocumentMetadata metadata, DocumentReference reference, String documentId) {
    this.content = content;
    this.metadata = metadata;
    this.reference = reference;
    this.documentId = documentId;
  }

  @Override
  public DocumentMetadata metadata() {
    return metadata;
  }

  @Override
  public String asBase64() {
    return Base64.getEncoder().encodeToString(content);
  }

  @Override
  public InputStream asInputStream() {
    return new ByteArrayInputStream(content);
  }

  @Override
  public byte[] asByteArray() {
    return content;
  }

  @Override
  public DocumentReference reference() {
    return reference;
  }

  @Override
  public String generateLink(DocumentLinkParameters parameters) {
    return "https://test/" + documentId;
  }
}
