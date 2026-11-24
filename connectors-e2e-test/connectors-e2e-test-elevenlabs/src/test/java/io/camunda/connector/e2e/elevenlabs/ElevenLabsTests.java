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
package io.camunda.connector.e2e.elevenlabs;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.process.test.api.CamundaAssert.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.client.CamundaClient;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Applies the real ElevenLabs element template to service tasks and runs them against a WireMock
 * stand-in for the ElevenLabs API.
 */
@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@SlowTest
class ElevenLabsTests {

  private static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/elevenlabs/element-templates/elevenlabs-connector.json";
  private static final String API_KEY = "inline-api-key";
  private static final byte[] AUDIO = {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 42};

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @TempDir File tempDir;

  @Autowired CamundaClient camundaClient;

  @Test
  void textToSpeechStoresTheAudioAsADocumentThatTranscribeAudioCanSend() {
    wm.stubFor(
        post(urlPathEqualTo("/v1/text-to-speech/voice123"))
            .withHeader("xi-api-key", equalTo(API_KEY))
            .withRequestBody(
                equalToJson("{\"text\": \"Hello\", \"model_id\": \"eleven_multilingual_v2\"}"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "audio/mpeg")
                    .withBody(AUDIO)));
    wm.stubFor(
        post(urlPathEqualTo("/v1/speech-to-text"))
            .withHeader("xi-api-key", equalTo(API_KEY))
            .withMultipartRequestBody(
                aMultipart().withName("model_id").withBody(equalTo("scribe_v1")))
            .withMultipartRequestBody(aMultipart().withName("file").withBody(binaryEqualTo(AUDIO)))
            .willReturn(okJson("{\"text\": \"Hello\", \"language_code\": \"eng\"}")));

    File textToSpeech =
        template("text_to_speech_full")
            .property("text_to_speech_full_path_voice_id", "voice123")
            .property(
                "text_to_speech_full_body",
                "={text: \"Hello\", model_id: \"eleven_multilingual_v2\"}")
            .property(
                "resultExpression",
                "={audio: response.document, audioType: response.document.metadata.contentType}")
            .writeTo(new File(tempDir, "tts.json"));
    File transcribe =
        template("speech_to_text")
            .property("speech_to_text_body", "={model_id: \"scribe_v1\", file: audio}")
            .property("resultExpression", "={transcript: response.body.text}")
            .writeTo(new File(tempDir, "stt.json"));

    BpmnModelInstance model =
        Bpmn.createProcess()
            .executable()
            .startEvent()
            .serviceTask("textToSpeech")
            .serviceTask("transcribe")
            .endEvent()
            .done();
    model = apply(model, textToSpeech, "textToSpeech");
    model = apply(model, transcribe, "transcribe");

    var test = run(model);

    assertThat(test.getProcessInstanceEvent())
        .hasVariable("audioType", "audio/mpeg")
        .hasVariable("transcript", "Hello");
  }

  @Test
  void listVoicesSendsOnlyTheQueryParametersThatAreSet() {
    wm.stubFor(
        get(urlPathEqualTo("/v2/voices"))
            .withHeader("xi-api-key", equalTo(API_KEY))
            .willReturn(
                okJson(
                    "{\"voices\": [{\"voice_id\": \"voice123\", \"name\": \"Rachel\"}],"
                        + " \"has_more\": false}")));

    File listVoices =
        template("get_user_voices_v2")
            .property("get_user_voices_v2_query_page_size", "5")
            .property("resultExpression", "={voiceId: response.body.voices[1].voice_id}")
            .writeTo(new File(tempDir, "voices.json"));

    var test = run(apply(singleTask("listVoices"), listVoices, "listVoices"));

    assertThat(test.getProcessInstanceEvent()).hasVariable("voiceId", "voice123");
    wm.verify(getRequestedFor(urlEqualTo("/v2/voices?page_size=5")));
  }

  @Test
  void submitBatchCallAuthenticatesWithTheReusableCredential() {
    wm.stubFor(
        post(urlPathEqualTo("/v1/convai/batch-calling/submit"))
            .withHeader("xi-api-key", equalTo("credential-api-key"))
            .withRequestBody(
                equalToJson(
                    "{\"call_name\": \"Renewals\", \"agent_id\": \"agent1\","
                        + " \"recipients\": [{\"phone_number\": \"+15551234567\"}]}"))
            .willReturn(okJson("{\"id\": \"batch1\", \"status\": \"pending\"}")));

    File batchCall =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("operationId", "create_batch_call")
            .property("baseUrl", wm.baseUrl())
            .property(
                "serviceAuthenticationConfiguration",
                "={authentication: {type: \"apiKey\", apiKeyLocation: \"headers\","
                    + " name: \"xi-api-key\", value: \"credential-api-key\"}}")
            .property(
                "create_batch_call_body",
                "={call_name: \"Renewals\", agent_id: \"agent1\","
                    + " recipients: [{phone_number: \"+15551234567\"}]}")
            .property("resultExpression", "={batchId: response.body.id}")
            .writeTo(new File(tempDir, "batch.json"));

    var test = run(apply(singleTask("submitBatchCall"), batchCall, "submitBatchCall"));

    assertThat(test.getProcessInstanceEvent()).hasVariable("batchId", "batch1");
  }

  private ElementTemplate template(String operationId) {
    return ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
        .property("operationId", operationId)
        .property("baseUrl", wm.baseUrl())
        .property("authentication.value.xi-api-key", API_KEY);
  }

  private static BpmnModelInstance singleTask(String taskId) {
    return Bpmn.createProcess().executable().startEvent().serviceTask(taskId).endEvent().done();
  }

  private BpmnModelInstance apply(BpmnModelInstance model, File template, String taskId) {
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, taskId + ".bpmn"))
        .apply(template, taskId, new File(tempDir, taskId + "-applied.bpmn"));
  }

  private ZeebeTest run(BpmnModelInstance model) {
    return ZeebeTest.with(camundaClient).deploy(model).createInstance().waitForProcessCompletion();
  }
}
