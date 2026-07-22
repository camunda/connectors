/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Focused round-trip coverage for every {@link AttributeValue} member this hand-rolled bridge
 * supports (S/N/BOOL/NUL/B/SS/NS/BS/M/L). The operation-level tests only exercise string and number
 * attributes; this class pins the remaining members directly so a type or serialization regression
 * in the shared bridge is caught close to the source instead of only surfacing (or not surfacing)
 * through some particular operation's golden fixture.
 */
class AttributeValueConverterTest {

  @Test
  void toAttributeValue_mapsString() {
    assertThat(AttributeValueConverter.toAttributeValue("hello"))
        .isEqualTo(AttributeValue.fromS("hello"));
  }

  @Test
  void toAttributeValue_mapsNumber() {
    assertThat(AttributeValueConverter.toAttributeValue(42)).isEqualTo(AttributeValue.fromN("42"));
    assertThat(AttributeValueConverter.toAttributeValue(new BigDecimal("3.140")))
        .isEqualTo(AttributeValue.fromN("3.140"));
  }

  @Test
  void toAttributeValue_mapsBoolean() {
    assertThat(AttributeValueConverter.toAttributeValue(true))
        .isEqualTo(AttributeValue.fromBool(true));
  }

  @Test
  void toAttributeValue_mapsNull() {
    assertThat(AttributeValueConverter.toAttributeValue(null))
        .isEqualTo(AttributeValue.fromNul(true));
  }

  @Test
  void toAttributeValue_mapsByteArray() {
    byte[] bytes = {1, 2, 3};
    assertThat(AttributeValueConverter.toAttributeValue(bytes))
        .isEqualTo(AttributeValue.fromB(SdkBytes.fromByteArray(bytes)));
  }

  @Test
  void toAttributeValue_mapsStringSet() {
    Set<String> set = new LinkedHashSet<>(List.of("a", "b", "c"));
    assertThat(AttributeValueConverter.toAttributeValue(set))
        .isEqualTo(AttributeValue.fromSs(List.of("a", "b", "c")));
  }

  @Test
  void toAttributeValue_mapsNumberSet() {
    Set<Number> set = new LinkedHashSet<>(List.of(1, 2, 3));
    assertThat(AttributeValueConverter.toAttributeValue(set))
        .isEqualTo(AttributeValue.fromNs(List.of("1", "2", "3")));
  }

  @Test
  void toAttributeValue_mapsByteArraySet() {
    byte[] first = {1, 2};
    byte[] second = {3, 4};
    Set<byte[]> set = new LinkedHashSet<>(List.of(first, second));
    assertThat(AttributeValueConverter.toAttributeValue(set))
        .isEqualTo(
            AttributeValue.fromBs(
                List.of(SdkBytes.fromByteArray(first), SdkBytes.fromByteArray(second))));
  }

  @Test
  void toAttributeValue_mapsEmptySetAsEmptyList() {
    // DynamoDB rejects empty SS/NS/BS outright, so an empty Set maps to an empty L instead.
    assertThat(AttributeValueConverter.toAttributeValue(Set.of()))
        .isEqualTo(AttributeValue.fromL(List.of()));
  }

  @Test
  void toAttributeValue_mapsMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", "1");
    map.put("count", 2);
    assertThat(AttributeValueConverter.toAttributeValue(map))
        .isEqualTo(
            AttributeValue.fromM(
                Map.of("id", AttributeValue.fromS("1"), "count", AttributeValue.fromN("2"))));
  }

  @Test
  void toAttributeValue_mapsList() {
    assertThat(AttributeValueConverter.toAttributeValue(List.of("a", 1)))
        .isEqualTo(
            AttributeValue.fromL(List.of(AttributeValue.fromS("a"), AttributeValue.fromN("1"))));
  }

  @Test
  void toAttributeValue_rejectsUnsupportedType() {
    assertThatThrownBy(() -> AttributeValueConverter.toAttributeValue(new Object()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported DynamoDB item value type");
  }

  @Test
  void toPlainValue_mapsString() {
    assertThat(AttributeValueConverter.toPlainValue(AttributeValue.fromS("hello")))
        .isEqualTo("hello");
  }

  @Test
  void toPlainValue_mapsNumber() {
    assertThat(AttributeValueConverter.toPlainValue(AttributeValue.fromN("42")))
        .isEqualTo(new BigDecimal("42"));
  }

  @Test
  void toPlainValue_mapsBoolean() {
    assertThat(AttributeValueConverter.toPlainValue(AttributeValue.fromBool(true))).isEqualTo(true);
  }

  @Test
  void toPlainValue_mapsNul() {
    assertThat(AttributeValueConverter.toPlainValue(AttributeValue.fromNul(true))).isNull();
  }

  @Test
  void toPlainValue_mapsNullAttributeValue() {
    assertThat(AttributeValueConverter.toPlainValue(null)).isNull();
  }

  @Test
  void toPlainValue_mapsByteArray() {
    byte[] bytes = {1, 2, 3};
    assertThat(
            (byte[])
                AttributeValueConverter.toPlainValue(
                    AttributeValue.fromB(SdkBytes.fromByteArray(bytes))))
        .isEqualTo(bytes);
  }

  @Test
  @SuppressWarnings("unchecked")
  void toPlainValue_mapsStringSet_toInsertionOrderedSet() {
    Object result =
        AttributeValueConverter.toPlainValue(AttributeValue.fromSs(List.of("a", "b", "c")));
    assertThat(result).isInstanceOf(Set.class);
    Set<String> stringSet = (Set<String>) result;
    assertThat(stringSet).containsExactly("a", "b", "c");
  }

  @Test
  @SuppressWarnings("unchecked")
  void toPlainValue_mapsNumberSet_toInsertionOrderedSet() {
    Object result =
        AttributeValueConverter.toPlainValue(AttributeValue.fromNs(List.of("1", "2", "3")));
    assertThat(result).isInstanceOf(Set.class);
    Set<BigDecimal> numberSet = (Set<BigDecimal>) result;
    assertThat(numberSet)
        .containsExactly(new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"));
  }

  @Test
  void toPlainValue_mapsByteArraySet_toSet() {
    byte[] first = {1, 2};
    byte[] second = {3, 4};
    Object result =
        AttributeValueConverter.toPlainValue(
            AttributeValue.fromBs(
                List.of(SdkBytes.fromByteArray(first), SdkBytes.fromByteArray(second))));
    assertThat(result).isInstanceOf(Set.class);
    assertThat((Set<?>) result).hasSize(2);
  }

  @Test
  @SuppressWarnings("unchecked")
  void toPlainValue_mapsMap_toMergedMap() {
    Map<String, AttributeValue> map =
        Map.of("id", AttributeValue.fromS("1"), "count", AttributeValue.fromN("2"));
    Object result = AttributeValueConverter.toPlainValue(AttributeValue.fromM(map));
    assertThat(result).isInstanceOf(Map.class);
    Map<String, Object> plainMap = (Map<String, Object>) result;
    assertThat(plainMap).containsEntry("id", "1").containsEntry("count", new BigDecimal("2"));
  }

  @Test
  void toPlainValue_mapsList() {
    Object result =
        AttributeValueConverter.toPlainValue(
            AttributeValue.fromL(List.of(AttributeValue.fromS("a"), AttributeValue.fromN("1"))));
    assertThat(result).isEqualTo(List.of("a", new BigDecimal("1")));
  }

  @Test
  void toPlainValue_rejectsUnsupportedType() {
    assertThatThrownBy(() -> AttributeValueConverter.toPlainValue(AttributeValue.builder().build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported DynamoDB AttributeValue type");
  }

  @Test
  void toAttributeValueMap_and_toPlainMap_roundTrip() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("id", "1");
    input.put("age", 30);
    input.put("active", true);

    Map<String, AttributeValue> attributeValues =
        AttributeValueConverter.toAttributeValueMap(input);
    Map<String, Object> roundTripped = AttributeValueConverter.toPlainMap(attributeValues);

    assertThat(roundTripped)
        .containsEntry("id", "1")
        .containsEntry("age", new BigDecimal("30"))
        .containsEntry("active", true);
  }

  @Test
  void toSingleKeyEntries_producesOneMapPerAttribute() {
    Map<String, AttributeValue> map = new LinkedHashMap<>();
    map.put("id", AttributeValue.fromS("1"));
    map.put("name", AttributeValue.fromS("Alice"));

    List<Map<String, Object>> entries = AttributeValueConverter.toSingleKeyEntries(map);

    assertThat(entries).hasSize(2);
    assertThat(entries.get(0)).containsExactly(Map.entry("id", "1"));
    assertThat(entries.get(1)).containsExactly(Map.entry("name", "Alice"));
  }
}
