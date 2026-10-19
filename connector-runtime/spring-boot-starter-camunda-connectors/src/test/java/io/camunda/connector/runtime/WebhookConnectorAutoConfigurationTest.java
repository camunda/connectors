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
package io.camunda.connector.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;

/**
 * Regresses the trust boundary {@link
 * WebhookConnectorAutoConfiguration#jackson2HttpMessageConverter} documents: this converter is
 * registered for every {@code @RequestBody}/{@code @ResponseBody} in the app, so its mapper must
 * never carry live {@code camunda.function.type} dispatch, unlike the allow-list-protected mappers
 * this fix disables dispatch on elsewhere. Exercises the converter's own {@code read}/{@code
 * write}, the exact extension points Spring MVC calls for request/response body binding -- not a
 * directly constructed mapper, which is what the existing agent-response test covers instead.
 */
class WebhookConnectorAutoConfigurationTest {

  private final WebhookConnectorAutoConfiguration configuration =
      new WebhookConnectorAutoConfiguration();

  @Test
  void requestBodyBinding_discriminatorShapedPayload_doesNotDispatchAnIntrinsicFunction()
      throws IOException {
    var converter = configuration.jackson2HttpMessageConverter();
    var body =
        """
        {"payload": {"camunda.function.type":"createLink",
                      "params":[{"camunda.document.type":"camunda"}, "PT1H"]}}
        """;
    var inputMessage = new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8));

    Object bound = converter.read(Map.class, null, inputMessage);

    // No document-deserializer module is registered on this mapper at all, so a discriminator
    // object binds as an ordinary nested Map -- proof no intrinsic function executor was ever
    // reached, not just that this particular call happened not to trigger one.
    assertThat(bound).isInstanceOf(Map.class);
    var payload = ((Map<?, ?>) bound).get("payload");
    assertThat(payload).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    var payloadMap = (Map<String, Object>) payload;
    assertThat(payloadMap)
        .containsEntry("camunda.function.type", "createLink")
        .doesNotContainKey("PT1H");
  }

  @Test
  void responseBodySerialization_documentField_stillSerializesItsReference() throws IOException {
    var converter = configuration.jackson2HttpMessageConverter();
    var metadata = new CamundaDocumentMetadataModel(null, null, null, null, null, null, null);
    var reference = new CamundaDocumentReferenceModel("store-1", "doc-1", "hash-1", metadata);
    var document = mock(Document.class);
    when(document.reference()).thenReturn(reference);
    var outputMessage = new MockHttpOutputMessage();

    converter.write(Map.of("document", document), MediaType.APPLICATION_JSON, outputMessage);

    var json = outputMessage.getBodyAsString(StandardCharsets.UTF_8);
    // A Document with no serializer registered for it would fall back to Jackson's default
    // bean introspection and serialize as {} (no accessible fields) -- the TODO comment on the
    // bean this converter comes from exists specifically to avoid that regression.
    assertThat(json).contains("\"camunda.document.type\":\"camunda\"").contains("doc-1");
  }

  @Test
  void
      demonstratesTheBypassThisBeanAvoids_aLiveDispatchMapperWouldExecuteTheSameDiscriminatedPayload()
          throws IOException {
    // Contrasts with requestBodyBinding_..._doesNotDispatchAnIntrinsicFunction above: if this
    // converter's mapper carried live dispatch the way the general-purpose mapper used to before
    // security-testing-findings#275's fix, the identical discriminator-shaped payload would
    // execute a registered intrinsic function during request binding, before any application
    // code -- let alone an allow-list check -- ever ran. This is what proves the test above is
    // discriminating, not vacuous: the same read call, against a mapper wired the old way,
    // dispatches.
    var vulnerableMapper =
        ConnectorsObjectMapperSupplier.getCopy()
            .registerModule(
                new JacksonModuleDocumentDeserializer(
                    mock(DocumentFactory.class),
                    new DefaultIntrinsicFunctionExecutor(ConnectorsObjectMapperSupplier.getCopy()),
                    DocumentModuleSettings.create()));
    var vulnerableConverter = new MappingJackson2HttpMessageConverter(vulnerableMapper);
    var body =
        """
        {"payload": {"camunda.function.type":"base64","params":["test"]}}
        """;
    var inputMessage = new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8));

    Object bound = vulnerableConverter.read(Map.class, null, inputMessage);

    @SuppressWarnings("unchecked")
    var boundMap = (Map<String, Object>) bound;
    // base64("test") -- proof the function actually executed during binding, not just that the
    // discriminator survived as inert data.
    assertThat(boundMap).containsEntry("payload", "dGVzdA==");
  }
}
