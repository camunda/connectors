/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpWebhookUtilTest {

  @Test
  void transformRawBodyToObject_XmlWithApplicationXmlContentType_ReturnsString() {
    // Given
    String xmlContent = "<request><id>123</id><status>active</status></request>";
    byte[] xmlBytes = xmlContent.getBytes(StandardCharsets.UTF_8);

    // When
    Object result = HttpWebhookUtil.transformRawBodyToObject(xmlBytes, "application/xml");

    // Then
    assertThat(result).isInstanceOf(String.class);
    assertThat(result).isEqualTo(xmlContent);
  }

  @Test
  void transformRawBodyToObject_ComplexXmlStructure_ReturnsString() {
    // Given
    String xmlContent =
        """
        <?xml version="1.0"?>
        <root>
          <user id="123">
            <name>Azan Baloch</name>
            <email>azan@example.com</email>
          </user>
          <action>create</action>
        </root>
        """;
    byte[] xmlBytes = xmlContent.getBytes(StandardCharsets.UTF_8);

    // When
    Object result = HttpWebhookUtil.transformRawBodyToObject(xmlBytes, "application/xml");

    // Then
    assertThat(result).isInstanceOf(String.class);
    assertThat(result).isEqualTo(xmlContent);
  }

  @Test
  void transformRawBodyToObject_XmlWithNamespace_ReturnsString() {
    // Given
    String xmlContent =
        "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soap:Body><data>test</data></soap:Body></soap:Envelope>";
    byte[] xmlBytes = xmlContent.getBytes(StandardCharsets.UTF_8);

    // When
    Object result = HttpWebhookUtil.transformRawBodyToObject(xmlBytes, "application/xml");

    // Then
    assertThat(result).isInstanceOf(String.class);
    assertThat(result).isEqualTo(xmlContent);
  }

  @Test
  void transformRawBodyToObject_MultipartFormData_ReturnsEmptyMap() {
    byte[] multipartBody =
        "--boundary\r\nContent-Disposition: form-data; name=\"file\"\r\n\r\ncontent\r\n"
            .getBytes(StandardCharsets.UTF_8);

    Object result =
        HttpWebhookUtil.transformRawBodyToObject(
            multipartBody, "multipart/form-data; boundary=boundary;charset=UTF-8");

    assertThat(result).isEqualTo(Map.of());
  }

  private static Object form(String body) {
    return HttpWebhookUtil.transformRawBodyToObject(
        body.getBytes(StandardCharsets.UTF_8), "application/x-www-form-urlencoded");
  }

  @Test
  void formBody_encodedSeparatorsInValue_arePreserved() {
    assertThat(form("a=Tom+%26+Jerry&b=x%3Dy")).isEqualTo(Map.of("a", "Tom & Jerry", "b", "x=y"));
  }

  @Test
  void formBody_literalEqualsInValue_isPreserved() {
    assertThat(form("sig=abc==&k=v")).isEqualTo(Map.of("sig", "abc==", "k", "v"));
  }

  @Test
  void formBody_encodedKey_emptyValueBareKeyAndEmptySegments() {
    assertThat(form("a%26b=1&k=&bare&&")).isEqualTo(Map.of("a&b", "1", "k", "", "bare", ""));
  }

  @Test
  void formBody_repeatedKey_firstValueWinsWithoutThrowing() {
    assertThat(form("a=1&a=2")).isEqualTo(Map.of("a", "1"));
  }
}
