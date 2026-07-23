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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReturn;
import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.api.document.DocumentReturnFormat;
import io.camunda.connector.api.document.InlineSizeGuard;
import io.camunda.connector.api.document.RawPayload;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentReturnProcessorTest {

  private final TestDocumentFactory factory = new TestDocumentFactory();
  private final ObjectMapper mapper = new ObjectMapper();
  private final DocumentReturnProcessor processor = new DocumentReturnProcessor(factory, mapper);

  private static DocumentReturnFormat format(DocumentReturnChoice choice) {
    return new DocumentReturnFormat(choice, null);
  }

  private static DocumentReturnFormat format(DocumentReturnChoice choice, String encoding) {
    return new DocumentReturnFormat(choice, encoding);
  }

  @Test
  void documentChoiceUploadsToStoreAndPassesDocumentToWrap() {
    byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
    RawPayload payload = RawPayload.of(bytes, "text/plain", "hello.txt");

    DocumentReturn<String> ret =
        new DocumentReturn<>(
            payload,
            (converted, choice) -> {
              assertThat(choice).isEqualTo(DocumentReturnChoice.DOCUMENT);
              assertThat(converted).isInstanceOf(Document.class);
              return ((Document) converted).metadata().getFileName();
            });

    Object result = processor.process(ret, format(DocumentReturnChoice.DOCUMENT));
    assertThat(result).isEqualTo("hello.txt");
  }

  @Test
  void textChoiceDecodesWithDefaultUtf8() {
    byte[] bytes = "café".getBytes(StandardCharsets.UTF_8);
    RawPayload payload = RawPayload.of(bytes, null, null);

    DocumentReturn<String> ret =
        new DocumentReturn<>(payload, (converted, choice) -> (String) converted);

    assertThat(processor.process(ret, format(DocumentReturnChoice.TEXT))).isEqualTo("café");
  }

  @Test
  void textChoiceHonorsExplicitEncoding() {
    byte[] bytes = "café".getBytes(StandardCharsets.ISO_8859_1);
    RawPayload payload = RawPayload.of(bytes, null, null);

    DocumentReturn<String> ret =
        new DocumentReturn<>(payload, (converted, choice) -> (String) converted);

    assertThat(processor.process(ret, format(DocumentReturnChoice.TEXT, "ISO-8859-1")))
        .isEqualTo("café");
  }

  @Test
  void jsonChoiceParsesToTreeForFeelDotAccess() {
    String json = "{\"name\": \"alice\", \"tags\": [\"a\", \"b\"]}";
    RawPayload payload =
        RawPayload.of(json.getBytes(StandardCharsets.UTF_8), "application/json", null);

    DocumentReturn<Object> ret = new DocumentReturn<>(payload, (converted, choice) -> converted);

    Object result = processor.process(ret, format(DocumentReturnChoice.JSON));
    assertThat(result).isInstanceOf(Map.class);
    Map<?, ?> map = (Map<?, ?>) result;
    assertThat(map.get("name")).isEqualTo("alice");
    assertThat(map.get("tags")).isInstanceOf(List.class);
  }

  @Test
  void jsonChoiceFailsOnInvalidJson() {
    byte[] bytes = "not-json {".getBytes(StandardCharsets.UTF_8);
    RawPayload payload = RawPayload.of(bytes, null, null);

    DocumentReturn<Object> ret = new DocumentReturn<>(payload, (converted, choice) -> converted);

    assertThatThrownBy(() -> processor.process(ret, format(DocumentReturnChoice.JSON)))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("not valid JSON");
  }

  @Test
  void nullFormatIsRejected() {
    RawPayload payload = RawPayload.of(new byte[0], null, null);
    DocumentReturn<Object> ret = new DocumentReturn<>(payload, (c, ch) -> c);

    assertThatThrownBy(() -> processor.process(ret, null))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("documentReturnFormat.choice");
  }

  @Test
  void textChoiceFailsFastWhenPayloadExceedsInlineLimit() {
    byte[] bytes = new byte[(int) InlineSizeGuard.MAX_INLINE_BYTES + 1];
    RawPayload payload = RawPayload.of(bytes, null, null);

    DocumentReturn<String> ret =
        new DocumentReturn<>(payload, (converted, choice) -> (String) converted);

    assertThatThrownBy(() -> processor.process(ret, format(DocumentReturnChoice.TEXT)))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Text response payload")
        .hasMessageContaining("Document reference");
  }

  @Test
  void jsonChoiceFailsFastWhenPayloadExceedsInlineLimit() {
    byte[] bytes = new byte[(int) InlineSizeGuard.MAX_INLINE_BYTES + 1];
    RawPayload payload = RawPayload.of(bytes, "application/json", null);

    DocumentReturn<Object> ret = new DocumentReturn<>(payload, (converted, choice) -> converted);

    assertThatThrownBy(() -> processor.process(ret, format(DocumentReturnChoice.JSON)))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("JSON response payload")
        .hasMessageContaining("Document reference");
  }

  @Test
  void textChoiceStopsReadingOnceInlineLimitIsExceeded() {
    // A stream that throws if read beyond the limit proves the size guard fires without buffering
    // the whole (potentially multi-GB) remote object into heap first.
    RawPayload payload =
        RawPayload.of(throwIfReadBeyond(InlineSizeGuard.MAX_INLINE_BYTES + 2), null, null);

    DocumentReturn<String> ret =
        new DocumentReturn<>(payload, (converted, choice) -> (String) converted);

    assertThatThrownBy(() -> processor.process(ret, format(DocumentReturnChoice.TEXT)))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("exceeds the inline variable size limit");
  }

  @Test
  void jsonChoiceStopsReadingOnceInlineLimitIsExceeded() {
    RawPayload payload =
        RawPayload.of(
            throwIfReadBeyond(InlineSizeGuard.MAX_INLINE_BYTES + 2), "application/json", null);

    DocumentReturn<Object> ret = new DocumentReturn<>(payload, (converted, choice) -> converted);

    assertThatThrownBy(() -> processor.process(ret, format(DocumentReturnChoice.JSON)))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("exceeds the inline variable size limit");
  }

  @Test
  void invalidEncodingDoesNotFailJsonDownload() {
    // Encoding only applies to TEXT: a stale/invalid encoding must not fail an otherwise valid JSON
    // download.
    String json = "{\"name\": \"alice\"}";
    RawPayload payload =
        RawPayload.of(json.getBytes(StandardCharsets.UTF_8), "application/json", null);

    DocumentReturn<Object> ret = new DocumentReturn<>(payload, (converted, choice) -> converted);

    Object result =
        processor.process(ret, format(DocumentReturnChoice.JSON, "definitely-not-a-charset"));
    assertThat(result).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) result).get("name")).isEqualTo("alice");
  }

  @Test
  void invalidEncodingDoesNotFailDocumentDownload() {
    RawPayload payload =
        RawPayload.of("hi".getBytes(StandardCharsets.UTF_8), "text/plain", "f.txt");

    DocumentReturn<Object> ret = new DocumentReturn<>(payload, (converted, choice) -> converted);

    Object result =
        processor.process(ret, format(DocumentReturnChoice.DOCUMENT, "definitely-not-a-charset"));
    assertThat(result).isInstanceOf(Document.class);
  }

  @Test
  void invalidEncodingStillFailsTextDownload() {
    RawPayload payload = RawPayload.of("hi".getBytes(StandardCharsets.UTF_8), null, null);

    DocumentReturn<Object> ret = new DocumentReturn<>(payload, (converted, choice) -> converted);

    assertThatThrownBy(
            () ->
                processor.process(
                    ret, format(DocumentReturnChoice.TEXT, "definitely-not-a-charset")))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("Unsupported charset");
  }

  /**
   * Returns a stream that yields {@code limit} zero-bytes and then throws — so any code that reads
   * past {@code limit} triggers the failure, but a read bounded at {@code MAX_INLINE_BYTES + 1}
   * does not.
   */
  private static InputStream throwIfReadBeyond(long limit) {
    return new InputStream() {
      private long served = 0;

      @Override
      public int read() throws IOException {
        if (served >= limit) {
          throw new IOException("stream should not be fully buffered; read past " + limit);
        }
        served++;
        return 0;
      }
    };
  }
}
