/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.csv;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

import io.camunda.connector.csv.model.*;
import io.camunda.connector.csv.model.ReadCsvRequest.RowType;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;

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
    var request = new ReadCsvRequest(csv, new CsvFormat(",", true, null), RowType.Object);
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
    var request = new ReadCsvRequest(productsCsv, new CsvFormat(",", true, null), RowType.Object);
    var mapper =
        (Function<Map<String, Object>, Object>)
            context -> {
              Map<String, String> record = (Map<String, String>) context.get("record");
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
  public void testReadCsvWithArrayType() {
    var request = new ReadCsvRequest(csv, new CsvFormat(",", true, null), RowType.Array);
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
    var request = new ReadCsvRequest(csv, new CsvFormat(",", true, null), RowType.Object);
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
            csv, new CsvFormat(",", true, List.of("the_name", "the_role")), RowType.Object);
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
    var request = new ReadCsvRequest(csv, new CsvFormat(",", false, null), RowType.Object);
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

  private static List<Map<String, Object>> toList(ReadCsvResult result) {
    return result.records().stream().map(r -> (Map<String, Object>) r).toList();
  }
}
