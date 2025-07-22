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
package io.camunda.document;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.client.api.response.DocumentMetadata;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;

class ExternalDocumentTest {

  @Mock HttpURLConnection mockConnection;

  String mockUrl = "http://example.com";
  byte[] fileContent = "test content".getBytes();

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this);
    when(mockConnection.getContentLengthLong()).thenReturn((long) fileContent.length);
    when(mockConnection.getContentType()).thenReturn("application/pdf");
    when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(fileContent));
    when(mockConnection.getURL()).thenReturn(new URL(mockUrl));
  }

  @Test
  void shouldLoadMetadataCorrectlyWithGivenName() throws Exception {
    HttpURLConnection mockConnection = mock(HttpURLConnection.class);
    when(mockConnection.getContentLengthLong()).thenReturn(1234L);
    when(mockConnection.getContentType()).thenReturn("application/pdf");
    URL fakeUrl = new URL("http://example.com");
    ExternalDocument doc = new ExternalDocument(fakeUrl.toString(), "test.pdf", mockConnection);

    DocumentMetadata metadata = doc.metadata();

    assertEquals("test.pdf", metadata.getFileName());
    assertEquals(1234L, metadata.getSize());
    assertEquals("application/pdf", metadata.getContentType());
  }

  @Test
  void shouldGenerateRandomFileNameIfNameIsNull() {
    ExternalDocument doc = new ExternalDocument(mockUrl, null, mockConnection);
    DocumentMetadata metadata = doc.metadata();

    assertNotNull(metadata.getFileName());
    assertDoesNotThrow(() -> UUID.fromString(metadata.getFileName()));
  }

  @Test
  void shouldReturnCorrectBase64Content() {
    ExternalDocument doc = new ExternalDocument(mockUrl, "test.txt", mockConnection);
    String base64 = doc.asBase64();

    assertEquals(Base64.getEncoder().encodeToString(fileContent), base64);
  }

  @Test
  void shouldReturnByteArray() {
    ExternalDocument doc = new ExternalDocument(mockUrl, "test.txt", mockConnection);
    byte[] bytes = doc.asByteArray();

    assertArrayEquals(fileContent, bytes);
  }

  @Test
  void shouldReturnUrlAsLink() {
    ExternalDocument doc = new ExternalDocument(mockUrl, "test.txt", mockConnection);

    String link = doc.generateLink(null);
    assertEquals(mockUrl, link);
  }

  @Test
  void shouldThrowExceptionWhenConnectionFails() throws Exception {
    try (MockedConstruction<URL> mockedURL =
        mockConstruction(
            URL.class,
            (mockUrlObj, context) -> {
              when(mockUrlObj.openConnection()).thenThrow(new IOException("Connection failed"));
            })) {

      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> new ExternalDocument(mockUrl, "fail.txt"));

      assertTrue(exception.getMessage().contains("Failed to connect to external document URL"));
    }
  }
}
