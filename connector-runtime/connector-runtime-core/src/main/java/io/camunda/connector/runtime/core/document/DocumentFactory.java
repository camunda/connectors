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

import io.camunda.connector.api.document.BasicDocument;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentContent.TransientDocumentContent;
import io.camunda.connector.api.document.DocumentSource;
import io.camunda.connector.api.document.DocumentSource.ByteArrayDocumentSource;
import io.camunda.connector.api.document.DocumentSource.ReferenceDocumentSource;

public class DocumentFactory {

  private TransientDataTransaction transientDataWriteContext = null;

  public DocumentFactory(TransientDataTransaction transientDataWriteContext) {
    this.transientDataWriteContext = transientDataWriteContext;
  }

  public DocumentFactory() {}

  public Document createDocument(DocumentSource documentSource) {
    switch (DocumentSource) {
      case ByteArrayDocumentSource byteArrayDocumentSource:
        return createDocument(byteArrayDocumentSource);
      case ReferenceDocumentSource referenceDocumentSource:
        return createDocument(referenceDocumentSource);
      default:
        throw new IllegalArgumentException(
            "Unsupported document source type: " + documentSource.getClass());
    }
  }

  public Document transformTransientDocumentToStatic(Document transientDocument) {
    var content = transientDocument.getContent();
    if (content instanceof TransientDocumentContent) {
      // TODO: create document
    } else {
      throw new IllegalArgumentException("Document content is not transient");
    }
  }

  private Document createByteArrayDocument(ByteArrayDocumentSource byteArrayDocumentSource) {
    byte[] content = byteArrayDocumentSource.content();
    String dataId = transientDataWriteContext.put(content);
    return new BasicDocument(dataId, new DocumentContentImpl(content));
  }
}
