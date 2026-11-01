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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CamundaDocumentTest {

  private final CamundaDocumentReference reference = mock(CamundaDocumentReference.class);
  private final CamundaDocumentStore documentStore = mock(CamundaDocumentStore.class);
  private final CamundaDocument document =
      new CamundaDocument(mock(DocumentMetadata.class), reference, documentStore);

  @Test
  void asByteArray_closesContentStream() {
    var content = new TrackingInputStream("hello".getBytes(StandardCharsets.UTF_8), false);
    when(documentStore.getDocumentContent(reference)).thenReturn(content);

    assertThat(document.asByteArray()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    assertThat(content.closed).isTrue();
  }

  @Test
  void asByteArray_closesContentStreamWhenReadFails() {
    var content = new TrackingInputStream(new byte[0], true);
    when(documentStore.getDocumentContent(reference)).thenReturn(content);

    assertThatThrownBy(document::asByteArray)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to read document content");
    assertThat(content.closed).isTrue();
  }

  private static class TrackingInputStream extends ByteArrayInputStream {
    private final boolean failOnRead;
    private boolean closed;

    TrackingInputStream(byte[] buf, boolean failOnRead) {
      super(buf);
      this.failOnRead = failOnRead;
    }

    @Override
    public synchronized byte[] readAllBytes() {
      if (failOnRead) {
        throw new IllegalStateException("connection reset");
      }
      return super.readAllBytes();
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}
