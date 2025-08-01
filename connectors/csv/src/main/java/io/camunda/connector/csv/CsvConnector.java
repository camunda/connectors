/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.csv;

import static io.camunda.connector.csv.CsvUtils.createCsvRequest;
import static io.camunda.connector.csv.CsvUtils.readCsvRequest;

import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.csv.model.*;
import io.camunda.connector.csv.model.ReadCsvRequest.RowType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.document.Document;
import io.camunda.document.store.DocumentCreationRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Optional;

/** Connector for reading and writing CSV files. */
@OutboundConnector(name = "CSV Connector", type = "io.camunda:csv-connector")
@ElementTemplate(
    name = "CSV Connector",
    id = "io.camunda.connectors.csv",
    engineVersion = "^8.8",
    icon = "icon.svg")
public class CsvConnector implements OutboundConnectorProvider {

  @Operation(id = "readCsv", name = "Read CSV")
  public ReadCsvResult readCsv(@Variable ReadCsvRequest request) {
    var rowType = Optional.ofNullable(request.rowType()).orElse(RowType.Object);
    return switch (request.data()) {
      case String csv -> readCsvRequest(new StringReader(csv), request.format(), rowType);
      case Document csv -> {
        try (InputStream csvInputStream = csv.asInputStream()) {
          yield readCsvRequest(new InputStreamReader(csvInputStream), request.format(), rowType);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported CSV data type: " + request.data().getClass().getSimpleName());
    };
  }

  @Operation(id = "writeCsv", name = "Write CSV")
  public Object writeCsv(@Variable WriteCsvRequest request, OutboundConnectorContext context) {
    var csv = createCsvRequest(request.data(), request.format());
    if (request.createDocument()) {
      var documentCreationRequest =
          DocumentCreationRequest.from(csv.getBytes()).contentType("text/csv").build();
      var document = context.create(documentCreationRequest);
      return new WriteCsvResult.Document(document);
    } else {
      return new WriteCsvResult.Value(csv);
    }
  }
}
