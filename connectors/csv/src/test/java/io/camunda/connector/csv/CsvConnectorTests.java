/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.csv;

import static java.util.Arrays.asList;
import static org.assertj.core.api.CollectionAssert.assertThatCollection;
import static org.junit.Assert.*;

import io.camunda.connector.csv.model.*;
import io.camunda.connector.csv.model.ReadCsvRequest.RowType;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.util.Map;
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
    var request = new ReadCsvRequest(csv, new CsvFormat(",", true, null), RowType.object);
    ReadCsvResult.Objects result = (ReadCsvResult.Objects) connector.readCsv(request);
    var records = result.records();
    assertNotNull(records);
    assertEquals(4, records.size());
    assertThatCollection(records).contains(Map.of("name", "Simon", "role", "Engineering Manager"));
    assertThatCollection(records).contains(Map.of("name", "Nico", "role", "Principal Engineer"));
    assertThatCollection(records).contains(Map.of("name", "Mathias", "role", "Backend Engineer"));
    assertThatCollection(records).contains(Map.of("name", "Kalina", "role", "Product Manager"));
  }

  @Test
  public void testReadCsvWithArrayResult() {
    var request = new ReadCsvRequest(csv, new CsvFormat(",", true, null), RowType.object);
    ReadCsvResult.Objects result = (ReadCsvResult.Objects) connector.readCsv(request);
    var records = result.records();
    assertNotNull(records);
    assertEquals(4, records.size());
    assertEquals("Simon", result.records().getFirst().get("name"));
    assertEquals("Engineering Manager", result.records().getFirst().get("role"));
  }

  @Test
  public void testWriteCsv() {
    var context = OutboundConnectorContextBuilder.create().build();
    var request =
        new CreateCsvRequest(
            asList(
                asList("name", "role"),
                asList("Simon", "Engineering Manager"),
                asList("Mathias", "Backend Engineer")),
            false,
            new CsvFormat(",", true, asList("name", "role")));
    var result = (CreateCsvResult.Value) connector.writeCsv(request, context);
    assertNotNull(result);
    assertEquals(
        "name,role\r\nSimon,Engineering Manager\r\nMathias,Backend Engineer\r\n", result.csv());
  }
}
