/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connectors.soap.SoapConnectorInput.SoapBodyPart;
import io.camunda.connectors.soap.SoapConnectorInput.SoapBodyPart.BodyJson;
import io.camunda.connectors.soap.SoapConnectorInput.SoapBodyPart.BodyTemplate;
import io.camunda.connectors.soap.SoapConnectorInput.SoapHeaderPart;
import io.camunda.connectors.soap.SoapConnectorInput.SoapHeaderPart.HeaderJson;
import io.camunda.connectors.soap.SoapConnectorInput.SoapHeaderPart.HeaderNone;
import io.camunda.connectors.soap.SoapConnectorInput.SoapHeaderPart.HeaderTemplate;
import io.camunda.connectors.soap.message.SoapMessageHandler;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

public class SoapMessageHandlerTest {

  @TestFactory
  Stream<DynamicTest> shouldGenerateBody() {
    return Stream.of(new BodyTemplate(mockTemplate(), mockContext()), new BodyJson(mockJson()))
        .map(bodyPart -> (SoapBodyPart) bodyPart)
        .map(
            bodyPart ->
                new TestParameter<>(bodyPart, body -> assertThat(body).isEqualTo(mockXml())))
        .map(
            parameter ->
                DynamicTest.dynamicTest(
                    parameter.parameter().toString(), () -> shouldGenerateBody(parameter)));
  }

  @TestFactory
  Stream<DynamicTest> shouldGenerateHeader() {
    return Stream.of(
            new HeaderTemplate(mockTemplate(), mockContext()),
            new HeaderJson(mockJson()),
            new HeaderNone())
        .map(headerPart -> (SoapHeaderPart) headerPart)
        .map(
            headerPart -> {
              if (headerPart instanceof HeaderNone) {
                return new TestParameter<>(headerPart, header -> assertThat(header).isNull());
              }
              return new TestParameter<>(
                  headerPart, header -> assertThat(header).isEqualTo(mockXml()));
            })
        .map(
            parameter ->
                DynamicTest.dynamicTest(
                    parameter.parameter().toString(), () -> shouldGenerateHeader(parameter)));
  }

  private String mockXml() {
    return """
        <root><inner id="abc">value</inner></root>""";
  }

  private String mockTemplate() {
    return """
        <root><inner id="abc">{{inner}}</inner></root>""";
  }

  private Map<String, Object> mockContext() {
    return Map.of("inner", "value");
  }

  private Map<String, Object> mockJson() {
    return Map.of("root", Map.of("inner", Map.of("@id", "abc", "$content", "value")));
  }

  private void shouldGenerateBody(TestParameter<SoapBodyPart> parameter) {
    SoapMessageHandler handler = new SoapMessageHandler();
    String soapBody = handler.generateBody(parameter.parameter());
    parameter.asserter().accept(soapBody);
  }

  void shouldGenerateHeader(TestParameter<SoapHeaderPart> parameter) {
    SoapMessageHandler handler = new SoapMessageHandler();
    String soapHeader = handler.generateHeader(parameter.parameter());
    parameter.asserter().accept(soapHeader);
  }

  private record TestParameter<T>(T parameter, Consumer<String> asserter) {}
}
