/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public final class AwsDynamoDbAttributeValueMapper {

  private AwsDynamoDbAttributeValueMapper() {}

  public static Map<String, AttributeValue> toAttributeValueMap(final Object value) {
    if (value == null) {
      return Collections.emptyMap();
    }
    if (!(value instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("Expected a map value");
    }
    return map.entrySet().stream()
        .collect(
            Collectors.toMap(
                entry -> String.valueOf(entry.getKey()),
                entry -> toAttributeValue(entry.getValue())));
  }

  public static AttributeValue toAttributeValue(final Object value) {
    if (value == null) {
      return AttributeValue.builder().nul(true).build();
    }
    if (value instanceof AttributeValue attributeValue) {
      return attributeValue;
    }
    if (value instanceof Map<?, ?> map) {
      return AttributeValue.builder().m(toAttributeValueMap(map)).build();
    }
    if (value instanceof Collection<?> collection) {
      if (value instanceof Set<?> set) {
        if (set.isEmpty()) {
          return AttributeValue.builder().l(Collections.emptyList()).build();
        }
        if (set.stream().allMatch(String.class::isInstance)) {
          return AttributeValue.builder().ss(set.stream().map(Object::toString).toList()).build();
        }
        if (set.stream().allMatch(Number.class::isInstance)) {
          return AttributeValue.builder()
              .ns(set.stream().map(number -> ((Number) number).toString()).toList())
              .build();
        }
        if (set.stream().allMatch(byte[].class::isInstance)) {
          return AttributeValue.builder()
              .bs(set.stream().map(bytes -> SdkBytes.fromByteArray((byte[]) bytes)).toList())
              .build();
        }
      }
      return AttributeValue.builder()
          .l(collection.stream().map(AwsDynamoDbAttributeValueMapper::toAttributeValue).toList())
          .build();
    }
    if (value instanceof Boolean boolValue) {
      return AttributeValue.builder().bool(boolValue).build();
    }
    if (value instanceof Number numberValue) {
      return AttributeValue.builder().n(numberValue.toString()).build();
    }
    if (value instanceof byte[] bytes) {
      return AttributeValue.builder().b(SdkBytes.fromByteArray(bytes)).build();
    }
    return AttributeValue.builder().s(value.toString()).build();
  }

  public static Map<String, Object> toSimpleMap(final Map<String, AttributeValue> item) {
    if (item == null) {
      return null;
    }
    return item.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> toSimpleValue(entry.getValue())));
  }

  public static Object toSimpleValue(final AttributeValue attributeValue) {
    if (attributeValue == null) {
      return null;
    }
    if (attributeValue.s() != null) {
      return attributeValue.s();
    }
    if (attributeValue.n() != null) {
      return new BigDecimal(attributeValue.n());
    }
    if (attributeValue.bool() != null) {
      return attributeValue.bool();
    }
    if (Boolean.TRUE.equals(attributeValue.nul())) {
      return null;
    }
    if (attributeValue.hasSs()) {
      return attributeValue.ss();
    }
    if (attributeValue.hasNs()) {
      return attributeValue.ns().stream().map(BigDecimal::new).toList();
    }
    if (attributeValue.hasBs()) {
      return attributeValue.bs().stream().map(SdkBytes::asByteArray).toList();
    }
    if (attributeValue.hasL()) {
      return attributeValue.l().stream()
          .map(AwsDynamoDbAttributeValueMapper::toSimpleValue)
          .toList();
    }
    if (attributeValue.hasM()) {
      return toSimpleMap(attributeValue.m());
    }
    return null;
  }
}
