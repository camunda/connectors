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
package io.camunda.connector.e2e.agenticai.aiagent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.core.io.ClassPathResource;

/**
 * Building blocks shared by the document flow e2e tests of both agent flavors: the scripted model
 * turns, the upload endpoint the {@code Upload_A_File} tool posts to and the assertions on what it
 * received.
 *
 * <p>The scripted model learns document ids through {@link
 * io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer}: {@code
 * documentId(0)} is the first document the request it answers knows.
 */
public final class DocumentFlowFixtures {

  public static final String UPLOAD_PATH = "/upload";
  public static final String DOWNLOADED_FILE = "test.pdf";
  public static final String DOWNLOAD_CALL_ID = "call_download";
  public static final String UPLOAD_CALL_ID = "call_upload";
  public static final String GET_CALL_ID = "call_get";
  public static final String GET_AGAIN_CALL_ID = "call_get_again";
  public static final String PING_CALL_ID = "call_ping";
  public static final String FINAL_TEXT = "I uploaded the document and return it.";

  private DocumentFlowFixtures() {}

  /** Stubs the endpoint the {@code Upload_A_File} tool posts the document to. */
  public static void stubUploadEndpoint() {
    stubFor(post(urlPathEqualTo(UPLOAD_PATH)).willReturn(aResponse().withStatus(200)));
  }

  public static String uploadUrl(String wireMockBaseUrl) {
    return wireMockBaseUrl + UPLOAD_PATH;
  }

  public static Turn downloadTurn(String wireMockBaseUrl) {
    return downloadTurn(wireMockBaseUrl, DOWNLOADED_FILE);
  }

  public static Turn downloadTurn(String wireMockBaseUrl, String file) {
    return downloadTurn(wireMockBaseUrl, file, DOWNLOAD_CALL_ID);
  }

  public static Turn downloadTurn(String wireMockBaseUrl, String file, String callId) {
    return Turn.toolCalls(
        "I will download the file.",
        10,
        20,
        ToolCall.of(
            callId, "Download_A_File", "{\"url\": \"%s/%s\"}".formatted(wireMockBaseUrl, file)));
  }

  /**
   * The {@code <doc />} tag of the document, escaped for a JSON string of the scripted arguments: a
   * document parameter takes the tag of a document.
   */
  public static String tag(String documentId) {
    return "<doc id=\\\"" + documentId + "\\\" />";
  }

  /** Loads the first known document of the request. */
  public static Turn getDocumentTurn() {
    return getDocumentTurn(documentId(0));
  }

  public static Turn getDocumentTurn(String documentId) {
    return getDocumentTurn(GET_CALL_ID, documentId);
  }

  public static Turn getDocumentTurn(String callId, String documentId) {
    return Turn.toolCalls(
        "I will read the document.",
        10,
        20,
        ToolCall.of(callId, "c8__get_document", "{\"id\": \"%s\"}".formatted(tag(documentId))));
  }

  /** Uploads the first known document of the request. */
  public static Turn uploadTurn() {
    return uploadTurn(documentId(0));
  }

  public static Turn uploadTurn(String documentId) {
    return Turn.toolCalls(
        "I will upload the document.",
        10,
        20,
        ToolCall.of(
            UPLOAD_CALL_ID, "Upload_A_File", "{\"document\": \"%s\"}".formatted(tag(documentId))));
  }

  /**
   * One assistant message with an upload with the tag of an unknown document and a {@code Ping}
   * call.
   */
  public static Turn unknownUploadWithPingTurn() {
    return Turn.toolCalls(
        "I will upload the document and check that the tools work.",
        10,
        20,
        ToolCall.of(
            UPLOAD_CALL_ID,
            "Upload_A_File",
            "{\"document\": \"%s\"}".formatted(tag("no-such-document"))),
        ToolCall.of(PING_CALL_ID, "Ping", "{}"));
  }

  public static Turn finalTurn() {
    return Turn.text(FINAL_TEXT, 10, 20);
  }

  /** The bytes of a file the WireMock server serves, as downloaded by the agent. */
  public static byte[] servedFile(String name) {
    try {
      return new ClassPathResource("__files/" + name).getContentAsByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Asserts that the upload endpoint received exactly one multipart request whose {@code file} part
   * holds exactly the given bytes, next to the plain {@code label} part.
   */
  public static void assertUploaded(byte[] expectedBytes) {
    final var uploads = findAll(postRequestedFor(urlPathEqualTo(UPLOAD_PATH)));
    assertThat(uploads).as("requests to the upload endpoint").hasSize(1);
    final var upload = uploads.getFirst();
    assertThat(upload.getHeader("Content-Type")).startsWith("multipart/form-data");
    assertThat(upload.getPart("file")).as("file part").isNotNull();
    assertThat(upload.getPart("file").getBody().asBytes())
        .as("uploaded bytes")
        .isEqualTo(expectedBytes);
    assertThat(upload.getPart("label").getBody().asString()).isEqualTo("from-agent");
  }

  public static int uploadCount() {
    return findAll(postRequestedFor(urlPathEqualTo(UPLOAD_PATH))).size();
  }
}
