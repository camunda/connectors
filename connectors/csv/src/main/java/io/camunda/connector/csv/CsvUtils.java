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
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

public class CsvUtils {

  static ReadCsvResult readCsvRequest(
      Reader csvReader,
      CsvFormat format,
      ReadCsvRequest.RowType rowType,
      Function<Map<String, Object>, Object> mapper) {
    try {
      var csvFormat = CsvUtils.buildFrom(format, rowType);
      var csvParser = csvFormat.parse(csvReader);
      return new ReadCsvResult(
          csvParser.stream()
              .map(record -> mapToRowType(record, rowType))
              .map(row -> mapRecord(row, mapper))
              .filter(Objects::nonNull)
              .toList());
    } catch (Throwable e) {
      throw new RuntimeException("Error reading CSV data", e);
    }
  }

  private static Object mapToRowType(CSVRecord record, ReadCsvRequest.RowType rowType) {
    return switch (rowType) {
      case Object -> record.toMap();
      case Array -> record.toList();
    };
  }

  private static Object mapRecord(Object record, Function<Map<String, Object>, Object> mapper) {
    if (mapper != null) {
      return mapper.apply(Map.of("record", record));
    } else {
      return record;
    }
  }

  static String createCsv(List<?> data, CsvFormat format) {
    var csvFormat = CsvUtils.buildFrom(format, null);
    var stringWriter = new StringWriter();
    try {
      CSVPrinter printer = new CSVPrinter(stringWriter, csvFormat);
      for (Object record : data) {
        if (record instanceof List<?> listValues) {
          printer.printRecord(listValues);
        } else if (record instanceof Map<?, ?> mapValues) {
          var row = format.headers().stream().map(mapValues::get).toList();
          printer.printRecord(row);
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
    CSVFormat.Builder builder =
        CSVFormat.Builder.create().setSkipHeaderRecord(format.skipHeaderRecord());
    if (format.delimiter() != null) {
      builder.setDelimiter(format.delimiter().trim());
    }
    if (headersDefined(format)) {
      String[] headers = format.headers().toArray(new String[0]);
      builder.setHeader(headers);
    } else {
      if (isObjectTypeRow(rowType) && !format.skipHeaderRecord()) {
        throw new IllegalArgumentException(
            "Headers must be defined when 'skipHeaderRecord' is true and row type is Object.");
      }
      builder.setHeader();
    }
    return builder.get();
  }

  private static boolean isObjectTypeRow(ReadCsvRequest.RowType rowType) {
    return rowType == ReadCsvRequest.RowType.Object;
  }

  private static boolean headersDefined(CsvFormat format) {
    return format.headers() != null && !format.headers().isEmpty();
  }
}
