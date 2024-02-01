/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class XmlToJsonMapperTest {

  @Test
  void shouldParseSimpleXmlString() {
    ToJsonMapper mapper = Mapper.toJson().build();
    Map<String, Object> result = mapper.toJson("<foo>bar</foo>");
    assertThat(result).contains(entry("foo", "bar"));
  }

  @Test
  void shouldGenerateJsonWithoutNamespaces() throws JsonProcessingException {
    ToJsonMapper mapper = Mapper.toJson().withPreserveNamespaces(false).build();
    JsonNode json = mapper.toJson(parseXml("soap-message.xml"));
    assertTrue(json.has("Envelope"));
    assertTrue(json.get("Envelope").has("Header"));
    assertTrue(json.get("Envelope").has("Body"));
  }

  @Test
  void shouldGenerateJsonWithNamespaces() throws JsonProcessingException {
    ToJsonMapper mapper = Mapper.toJson().withPreserveNamespaces(true).build();
    Document initialDoc = parseXml("soap-message.xml");
    JsonNode json = mapper.toJson(initialDoc);
    assertTrue(json.has("soap:Envelope"));
    assertTrue(json.get("soap:Envelope").has("soap:Header"));
    assertTrue(json.get("soap:Envelope").has("soap:Body"));
  }

  @Test
  void shouldCreateArrayForSameChildNodes() throws JsonProcessingException {
    ToJsonMapper mapper = Mapper.toJson().build();
    mapper.toJson(parseXml("xml-list.xml"));
  }

  private Document parseXml(String filename) {
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(true);
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(filename)) {
      return documentBuilderFactory.newDocumentBuilder().parse(in);
    } catch (SAXException | IOException | ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "test1.json,test1.xml",
        "test2.json,test2.xml",
        "test3.json,test3.xml",
        "test4.json,test4.xml"
      })
  void shouldMap(String jsonFile, String xmlFile) throws JsonProcessingException {
    ToXmlMapper mapper = Mapper.toXml().build();
    ObjectMapper objectMapper = new ObjectMapper();
    String jsonString = getResource(jsonFile);
    String xmlString =
        mapper.toXmlString(objectMapper.readValue(jsonString, new TypeReference<>() {})).trim();
    String matcher = getResource(xmlFile).trim();
    assertThat(xmlString).isEqualTo(matcher);
  }

  private String getResource(String filename) {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(filename)) {
      return new String(in.readAllBytes());
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("Error while reading file '%s' from classpath", filename), e);
    }
  }
}
