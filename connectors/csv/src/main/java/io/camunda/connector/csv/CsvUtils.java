/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.csv;

import io.camunda.connector.csv.model.CsvFormat;
import io.camunda.connector.csv.model.ReadCsvRequest;
import io.camunda.connector.csv.model.ReadCsvResult;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

public class CsvUtils {

  static ReadCsvResult readCsvRequest(
      Reader csvReader, CsvFormat format, ReadCsvRequest.RowType rowType) {
    try {
      var csvFormat = CsvUtils.buildFrom(format, rowType);
      var csvParser = csvFormat.parse(csvReader);
      return switch (rowType) {
        case object -> new ReadCsvResult.Objects(csvParser.stream().map(CSVRecord::toMap).toList());
        case array -> new ReadCsvResult.Arrays(csvParser.stream().map(CSVRecord::toList).toList());
      };
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static String createCsvRequest(List<?> data, CsvFormat format) {
    var csvFormat = CsvUtils.buildFrom(format, null);
    var stringWriter = new StringWriter();
    try {
      CSVPrinter printer = new CSVPrinter(stringWriter, csvFormat);
      for (Object record : data) {
        if (record instanceof List<?> listValues) {
          printer.printRecord(listValues);
        } else if (record instanceof Map<?, ?> mapValues) {
          printer.printRecord(mapValues);
        } else {
          throw new IllegalArgumentException(
              "Unsupported record type: " + record.getClass().getSimpleName());
        }
      }
      return stringWriter.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static CSVFormat buildFrom(CsvFormat format, ReadCsvRequest.RowType rowType) {
    CSVFormat.Builder builder = CSVFormat.Builder.create();

    if (format.delimiter() != null) {
      builder.setDelimiter(format.delimiter().trim());
    }

    if (format.skipHeaderRecord() != null) {
      builder.setSkipHeaderRecord(format.skipHeaderRecord());
      if (format.skipHeaderRecord() && (format.headers() == null || format.headers().isEmpty())) {
        // First row is skipped, so we set the header to default (first row).
        builder.setHeader();
      }
    } else {
      // Object rows require a header by default.
      if (rowType == ReadCsvRequest.RowType.object
          && (format.headers() == null || format.headers().isEmpty())) {
        // First row is skipped, so we set the header to default (first row).
        builder.setHeader();
      }
    }

    if (format.headers() != null && !format.headers().isEmpty()) {
      String[] headers = format.headers().toArray(new String[format.headers().size()]);
      builder.setHeader(headers);
      if (format.skipHeaderRecord() != null && format.skipHeaderRecord()) {
        builder.setSkipHeaderRecord(true);
      }
    }

    return builder.build();
  }
}
