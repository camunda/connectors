/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.avro.AvroFactory;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;

public class GenericRecordEncoder {

  private static final ObjectMapper JACKSON_AVRO_MAPPER = new ObjectMapper(new AvroFactory());

  public ObjectNode encode(GenericRecord record) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); ) {
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
      DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(record.getSchema());
      writer.write(record, encoder);
      encoder.flush();
      return JACKSON_AVRO_MAPPER
          .readerFor(ObjectNode.class)
          .with(new AvroSchema(record.getSchema()))
          .readValue(outputStream.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
