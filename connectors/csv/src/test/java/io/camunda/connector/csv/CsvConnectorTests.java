/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.csv;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReturn;
import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.api.document.DocumentReturnFormat;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.csv.model.*;
import io.camunda.connector.csv.model.ReadCsvRequest.RowType;
import io.camunda.connector.runtime.test.document.TestDocument;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class CsvConnectorTests {

  String csv =
      """
        name,role
        Simon,Engineering Manager
        Nico,Principal Engineer
        Mathias,Backend Engineer
        Kalina,Product Manager
        """
          .stripIndent();

  CsvConnector connector = new CsvConnector();

  @Test
  public void testReadCsv() {
    var request =
        new ReadCsvRequest(null, doc(csv), new CsvFormat(",", true, null), RowType.Object);
    ReadCsvResult result = connector.readCsv(request, null);

    var records = toList(result);
    assertNotNull(records);
    assertEquals(4, records.size());
    assertThat(records).contains(Map.of("name", "Simon", "role", "Engineering Manager"));
    assertThat(records).contains(Map.of("name", "Nico", "role", "Principal Engineer"));
    assertThat(records).contains(Map.of("name", "Mathias", "role", "Backend Engineer"));
    assertThat(records).contains(Map.of("name", "Kalina", "role", "Product Manager"));
  }

  String productsCsv =
      """
            name,price
            Monitor,200
            Macbook,1200
            Mouse,10
            """
          .stripIndent();

  @Test
  public void testReadCsvWithMapper() {
    var request =
        new ReadCsvRequest(null, doc(productsCsv), new CsvFormat(",", true, null), RowType.Object);
    var mapper =
        (Function<Map<String, Object>, Object>)
            context -> {
              var record = (Map<String, String>) context.get("record");
              return Map.of(
                  "product", record.get("name"), "price", Integer.parseInt(record.get("price")));
            };
    ReadCsvResult result = connector.readCsv(request, mapper);

    var records = toList(result);
    assertNotNull(records);
    assertEquals(3, records.size());
    assertThat(records).contains(Map.of("product", "Monitor", "price", 200));
    assertThat(records).contains(Map.of("product", "Macbook", "price", 1200));
    assertThat(records).contains(Map.of("product", "Mouse", "price", 10));
  }

  @Test
  public void testReadCsvWithFilteringMapper() {
    var request =
        new ReadCsvRequest(null, doc(productsCsv), new CsvFormat(",", true, null), RowType.Object);
    var mapper =
        (Function<Map<String, Object>, Object>)
            context -> {
              var record = (Map<String, String>) context.get("record");
              var price = Integer.parseInt(record.get("price"));
              if (price > 1000) {
                return null; // Filter out products with price > 1000
              } else {
                return Map.of("product", record.get("name"), "price", price);
              }
            };
    ReadCsvResult result = connector.readCsv(request, mapper);

    var records = toList(result);
    assertNotNull(records);
    assertEquals(2, records.size());
    assertThat(records).contains(Map.of("product", "Monitor", "price", 200));
    assertThat(records).contains(Map.of("product", "Mouse", "price", 10));
  }

  @Test
  public void testReadCsvWithArrayType() {
    var request = new ReadCsvRequest(null, doc(csv), new CsvFormat(",", true, null), RowType.Array);
    ReadCsvResult result = connector.readCsv(request, null);

    var records = result.records();
    assertNotNull(records);
    assertEquals(4, records.size());
    assertThat(records.get(0)).isEqualTo(List.of("Simon", "Engineering Manager"));
    assertThat(records.get(1)).isEqualTo(List.of("Nico", "Principal Engineer"));
    assertThat(records.get(2)).isEqualTo(List.of("Mathias", "Backend Engineer"));
    assertThat(records.get(3)).isEqualTo(List.of("Kalina", "Product Manager"));
  }

  @Test
  public void testReadCsvWithObjectsType() {
    var request =
        new ReadCsvRequest(null, doc(csv), new CsvFormat(",", true, null), RowType.Object);
    ReadCsvResult result = connector.readCsv(request, null);

    var records = toList(result);
    assertNotNull(records);
    assertEquals(4, records.size());
    assertEquals("Simon", records.getFirst().get("name"));
    assertEquals("Engineering Manager", records.getFirst().get("role"));
  }

  @Test
  public void testReadCsvWithObjectsTypeAndHeaders() {
    var request =
        new ReadCsvRequest(
            null,
            doc(csv),
            new CsvFormat(",", true, List.of("the_name", "the_role")),
            RowType.Object);
    ReadCsvResult result = connector.readCsv(request, null);

    var records = toList(result);
    assertNotNull(records);
    assertEquals(4, records.size());
    records.forEach(
        record -> {
          assertTrue(record.containsKey("the_name"));
          assertTrue(record.containsKey("the_role"));
        });
    assertEquals("Simon", records.getFirst().get("the_name"));
    assertEquals("Engineering Manager", records.getFirst().get("the_role"));
  }

  @Test
  public void testReadCSVWithoutHeadersAndSkipHeaderRecord() {
    var request =
        new ReadCsvRequest(null, doc(csv), new CsvFormat(",", false, null), RowType.Object);
    assertThatRuntimeException().isThrownBy(() -> connector.readCsv(request, null));
  }

  @Test
  public void testWriteCsv() {
    var context = OutboundConnectorContextBuilder.create().build();
    var request =
        new WriteCsvRequest(
            asList(
                asList("name", "role"),
                asList("Simon", "Engineering Manager"),
                asList("Mathias", "Backend Engineer")),
            false,
            new CsvFormat(",", true, asList("name", "role")));
    var result = (WriteCsvResult.Value) connector.writeCsv(request, context);
    assertNotNull(result);
    assertEquals(
        "name,role\r\nSimon,Engineering Manager\r\nMathias,Backend Engineer\r\n", result.content());
  }

  @Test
  public void testWriteObjects() {
    var context = OutboundConnectorContextBuilder.create().build();
    var request =
        new WriteCsvRequest(
            asList(
                Map.of("name", "Simon", "role", "Engineering Manager"),
                Map.of("name", "Mathias", "role", "Backend Engineer")),
            false,
            new CsvFormat(",", true, asList("name", "role")));
    var result = (WriteCsvResult.Value) connector.writeCsv(request, context);
    assertNotNull(result);
    assertEquals("Simon,Engineering Manager\r\nMathias,Backend Engineer\r\n", result.content());
  }

  @Test
  public void testWriteObjectsAndAddHeaders() {
    var context = OutboundConnectorContextBuilder.create().build();
    var request =
        new WriteCsvRequest(
            asList(
                Map.of("name", "Simon", "role", "Engineering Manager"),
                Map.of("name", "Mathias", "role", "Backend Engineer")),
            false,
            new CsvFormat(",", false, asList("name", "role")));
    var result = (WriteCsvResult.Value) connector.writeCsv(request, context);
    assertNotNull(result);
    assertEquals(
        "name,role\r\nSimon,Engineering Manager\r\nMathias,Backend Engineer\r\n", result.content());
  }

  @Test
  public void testWriteObjectsWithoutHeadersThrows() {
    var context = OutboundConnectorContextBuilder.create().build();
    var nullHeaders =
        new WriteCsvRequest(
            asList(Map.of("name", "Simon", "role", "Engineering Manager")),
            false,
            new CsvFormat(",", true, null));
    assertThatThrownBy(() -> connector.writeCsv(nullHeaders, context))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("Headers must be defined");

    var emptyHeaders =
        new WriteCsvRequest(
            asList(Map.of("name", "Simon", "role", "Engineering Manager")),
            false,
            new CsvFormat(",", true, List.of()));
    assertThatThrownBy(() -> connector.writeCsv(emptyHeaders, context))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("Headers must be defined");
  }

  @Test
  public void testWriteCsvAsDocumentReturn() {
    var context = mock(OutboundConnectorContext.class);
    when(context.readDocumentReturnFormat())
        .thenReturn(Optional.of(new DocumentReturnFormat(DocumentReturnChoice.DOCUMENT, null)));
    var request =
        new WriteCsvRequest(
            asList(asList("name", "role"), asList("Simon", "Engineering Manager")),
            false,
            new CsvFormat(",", true, asList("name", "role")));

    var documentReturn = (DocumentReturn<?>) connector.writeCsv(request, context);

    // The runtime performs the actual conversion; here we assert the raw payload and the wrap.
    assertEquals("text/csv", documentReturn.payload().contentType());
    var wrapped = documentReturn.wrap().apply(doc("ignored"), DocumentReturnChoice.DOCUMENT);
    assertThat(wrapped).isInstanceOf(WriteCsvResult.Document.class);
  }

  @Test
  public void testWriteCsvAsTextReturn() {
    var context = mock(OutboundConnectorContext.class);
    when(context.readDocumentReturnFormat())
        .thenReturn(Optional.of(new DocumentReturnFormat(DocumentReturnChoice.TEXT, null)));
    var request =
        new WriteCsvRequest(
            asList(asList("name", "role"), asList("Simon", "Engineering Manager")),
            false,
            new CsvFormat(",", true, asList("name", "role")));

    var documentReturn = (DocumentReturn<?>) connector.writeCsv(request, context);
    var wrapped =
        (WriteCsvResult.Value)
            documentReturn.wrap().apply("name,role\r\n", DocumentReturnChoice.TEXT);
    assertEquals("name,role\r\n", wrapped.content());
  }

  @Test
  public void testReadCsvLegacyRawText() {
    // element-template <= v2 bound raw CSV text to `data`; new runtime must still accept it
    var request = new ReadCsvRequest(csv, null, new CsvFormat(",", true, null), RowType.Object);
    ReadCsvResult result = connector.readCsv(request, null);

    var records = toList(result);
    assertEquals(4, records.size());
    assertThat(records).contains(Map.of("name", "Simon", "role", "Engineering Manager"));
  }

  @Test
  public void testReadCsvLegacyDocument() {
    // element-template <= v2 bound a document reference to `data`; still supported
    var request =
        new ReadCsvRequest(doc(csv), null, new CsvFormat(",", true, null), RowType.Object);
    ReadCsvResult result = connector.readCsv(request, null);

    assertEquals(4, toList(result).size());
  }

  @Test
  public void testReadCsvDocumentTakesPrecedenceOverLegacyData() {
    // when both are present, the new `document` input wins
    var request =
        new ReadCsvRequest(
            "ignored,legacy", doc(csv), new CsvFormat(",", true, null), RowType.Object);
    ReadCsvResult result = connector.readCsv(request, null);

    assertEquals(4, toList(result).size());
  }

  @Test
  public void testReadCsvNoDataThrows() {
    var request = new ReadCsvRequest(null, null, new CsvFormat(",", true, null), RowType.Object);
    assertThatThrownBy(() -> connector.readCsv(request, null))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("No CSV data provided");
  }

  private static Document doc(String content) {
    return new TestDocument(content.getBytes(StandardCharsets.UTF_8), null, null, null);
  }

  private static List<Map<String, Object>> toList(ReadCsvResult result) {
    return result.records().stream().map(r -> (Map<String, Object>) r).toList();
  }
}
