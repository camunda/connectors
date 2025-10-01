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
package io.camunda.connector.api.document;

import java.io.InputStream;

/**
 * Represents a uniform document (file) object that can be passed between connectors and used in the
 * FEEL engine.
 */
public interface Document {

  /**
   * Domain-specific metadata that can be attached to the document. When a file is consumed by a
   * connector as input, the metadata originates from the
   */
  DocumentMetadata metadata();

  String asBase64();

  /**
   * Provides the document content as an InputStream. The caller is responsible for closing the
   * stream after use.
   */
  InputStream asInputStream();

  byte[] asByteArray();

  DocumentReference reference();

  String generateLink(DocumentLinkParameters parameters);
}
