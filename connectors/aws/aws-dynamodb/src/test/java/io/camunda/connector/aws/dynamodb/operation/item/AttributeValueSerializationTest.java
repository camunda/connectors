/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.operation.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.ObjectMapperSupplier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins the raw v1 {@link AttributeValue} JSON shape (lowercase field names, all-null padding around
 * whichever member is actually set) directly against the model class, independent of any
 * operation's response envelope.
 *
 * <p>This is deliberately NOT wired through {@code DeleteItemOperation}/{@code
 * UpdateItemOperation}: those operations invoke {@code Table.deleteItem(KeyAttribute...)} / {@code
 * Table.updateItem(PrimaryKey, AttributeUpdate...)}, neither of which sets {@code ReturnValues}, so
 * a live call can never actually get attributes back (see DeleteItemOperationTest /
 * UpdateItemOperationTest). The shape below only matters if this connector ever starts requesting
 * {@code ReturnValues=ALL_OLD}/{@code ALL_NEW}; it is pinned here so that future addition doesn't
 * have to rediscover the quirk from scratch.
 */
class AttributeValueSerializationTest {

  private static final ObjectMapper objectMapper = ObjectMapperSupplier.getMapperInstance();

  @Test
  void attributeValue_serializesToDocumentedV1JsonShape_acrossEveryMemberType() throws Exception {
    // Given every AttributeValue member type the raw v1 model supports.
    Map<String, AttributeValue> attributes = new LinkedHashMap<>();
    attributes.put("status", new AttributeValue().withS("Active"));
    attributes.put("age", new AttributeValue().withN("45"));
    attributes.put("tags", new AttributeValue().withSS("a", "b"));
    attributes.put("scores", new AttributeValue().withNS("1", "2"));
    attributes.put("flag", new AttributeValue().withBOOL(true));
    attributes.put("nickname", new AttributeValue().withNULL(true));
    attributes.put(
        "nested", new AttributeValue().withM(Map.of("inner", new AttributeValue().withS("value"))));
    attributes.put("list", new AttributeValue().withL(new AttributeValue().withS("x")));

    // When: serialized the same way the production mapper writes any AttributeValue-bearing
    // result to process variables. Built via readTree(writeValueAsString(...)), not
    // valueToTree(): see AddItemOperationTest for why (valueToTree() strips trailing zeroes off
    // BigDecimal values -- not relevant here, but kept consistent with the other golden tests in
    // this module).
    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(attributes));

    // Then: every member serializes with all ten lowercase AttributeValue fields present, only
    // the one matching member type populated and the rest explicit null.
    String expectedJson =
        """
        {
          "status": { "s": "Active", "n": null, "b": null, "m": null, "l": null,
                      "ss": null, "ns": null, "bs": null, "null": null, "bool": null },
          "age": { "s": null, "n": "45", "b": null, "m": null, "l": null,
                   "ss": null, "ns": null, "bs": null, "null": null, "bool": null },
          "tags": { "s": null, "n": null, "b": null, "m": null, "l": null,
                    "ss": ["a", "b"], "ns": null, "bs": null, "null": null, "bool": null },
          "scores": { "s": null, "n": null, "b": null, "m": null, "l": null,
                      "ss": null, "ns": ["1", "2"], "bs": null, "null": null, "bool": null },
          "flag": { "s": null, "n": null, "b": null, "m": null, "l": null,
                    "ss": null, "ns": null, "bs": null, "null": null, "bool": true },
          "nickname": { "s": null, "n": null, "b": null, "m": null, "l": null,
                        "ss": null, "ns": null, "bs": null, "null": true, "bool": null },
          "nested": { "s": null, "n": null, "b": null, "l": null,
                      "ss": null, "ns": null, "bs": null, "null": null, "bool": null,
                      "m": {
                        "inner": { "s": "value", "n": null, "b": null, "m": null, "l": null,
                                   "ss": null, "ns": null, "bs": null, "null": null, "bool": null }
                      } },
          "list": { "s": null, "n": null, "b": null, "m": null,
                    "ss": null, "ns": null, "bs": null, "null": null, "bool": null,
                    "l": [
                      { "s": "x", "n": null, "b": null, "m": null, "l": null,
                        "ss": null, "ns": null, "bs": null, "null": null, "bool": null }
                    ] }
        }
        """;
    JsonNode expected = objectMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);

    // Deliberately no exact writeValueAsString() pin here: AttributeValue is a plain, unannotated
    // JavaBean with no @JsonPropertyOrder, and Jackson's reflection-based property order for it
    // was empirically observed to change between separate JVM invocations of this exact test on
    // this exact SDK version (see AddItemOperationTest for details). Tree equality above -- which
    // compares JSON objects key-by-key regardless of order -- is the reliable way to pin this
    // shape.
  }
}
