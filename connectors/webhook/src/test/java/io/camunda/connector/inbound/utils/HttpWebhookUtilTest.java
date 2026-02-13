/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
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
}
