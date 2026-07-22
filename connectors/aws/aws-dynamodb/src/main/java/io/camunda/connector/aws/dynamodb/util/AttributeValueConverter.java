/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Hand-rolled JSON/plain-Java &lt;-&gt; AWS SDK v2 {@link AttributeValue} bridge.
 *
 * <p>AWS SDK v1's Document API (the {@code com.amazonaws.services.dynamodbv2.document} package --
 * {@code Item}, {@code PrimaryKey}, {@code KeyAttribute}, {@code AttributeUpdate}, ...) implicitly
 * converted plain Java/JSON values to/from raw {@code AttributeValue}s via {@code ItemUtils}. AWS
 * SDK v2 has no equivalent Document API (and this connector explicitly does not adopt the
 * dynamodb-enhanced client library), so this class reprograms that implicit mapping table by hand:
 *
 * <ul>
 *   <li>{@code String} &lt;-&gt; {@code S}
 *   <li>{@code Number} &lt;-&gt; {@code N} (a decimal string on the wire)
 *   <li>{@code Boolean} &lt;-&gt; {@code BOOL}
 *   <li>{@code null} &lt;-&gt; {@code NUL}
 *   <li>{@code byte[]} &lt;-&gt; {@code B}
 *   <li>{@code Map<String, ?>} &lt;-&gt; {@code M}
 *   <li>{@code List<?>} &lt;-&gt; {@code L}
 *   <li>homogeneous {@code Set<String>}/{@code Set<Number>}/{@code Set<byte[]>} &lt;-&gt; {@code
 *       SS}/{@code NS}/{@code BS}
 * </ul>
 */
public final class AttributeValueConverter {

  private AttributeValueConverter() {}

  // ---------------------------------------------------------------------------------------------
  // plain Java/JSON -> AttributeValue
  // ---------------------------------------------------------------------------------------------

  /** Converts a single plain Java/JSON value into an {@link AttributeValue}. */
  public static AttributeValue toAttributeValue(final Object value) {
    if (value == null) {
      return AttributeValue.fromNul(true);
    }
    if (value instanceof AttributeValue attributeValue) {
      return attributeValue;
    }
    if (value instanceof String string) {
      return AttributeValue.fromS(string);
    }
    if (value instanceof Boolean bool) {
      return AttributeValue.fromBool(bool);
    }
    if (value instanceof Number number) {
      return AttributeValue.fromN(numberToString(number));
    }
    if (value instanceof byte[] bytes) {
      return AttributeValue.fromB(SdkBytes.fromByteArray(bytes));
    }
    if (value instanceof Set<?> set) {
      return setToAttributeValue(set);
    }
    if (value instanceof Map<?, ?> map) {
      return AttributeValue.fromM(toAttributeValueMap(castMap(map)));
    }
    if (value instanceof Collection<?> collection) {
      return AttributeValue.fromL(
          collection.stream().map(AttributeValueConverter::toAttributeValue).toList());
    }
    throw new IllegalArgumentException(
        "Unsupported DynamoDB item value type: " + value.getClass().getName());
  }

  /** Converts a plain {@code Map<String, Object>} into a {@code Map<String, AttributeValue>}. */
  public static Map<String, AttributeValue> toAttributeValueMap(final Map<String, Object> values) {
    Map<String, AttributeValue> result = new LinkedHashMap<>();
    values.forEach((key, value) -> result.put(key, toAttributeValue(value)));
    return result;
  }

  private static AttributeValue setToAttributeValue(final Set<?> set) {
    if (set.isEmpty()) {
      // DynamoDB rejects empty SS/NS/BS sets outright; an empty list is the closest faithful
      // representation of "a collection with nothing in it".
      return AttributeValue.fromL(List.of());
    }
    Object first = set.iterator().next();
    if (first instanceof String) {
      return AttributeValue.fromSs(set.stream().map(String.class::cast).toList());
    }
    if (first instanceof Number) {
      return AttributeValue.fromNs(
          set.stream().map(element -> numberToString((Number) element)).toList());
    }
    if (first instanceof byte[]) {
      return AttributeValue.fromBs(
          set.stream().map(element -> SdkBytes.fromByteArray((byte[]) element)).toList());
    }
    throw new IllegalArgumentException(
        "Unsupported DynamoDB set element type: " + first.getClass().getName());
  }

  private static String numberToString(final Number number) {
    if (number instanceof BigDecimal bigDecimal) {
      return bigDecimal.toPlainString();
    }
    return number.toString();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(final Map<?, ?> map) {
    return (Map<String, Object>) map;
  }

  // ---------------------------------------------------------------------------------------------
  // AttributeValue -> plain Java/JSON
  // ---------------------------------------------------------------------------------------------

  /** Converts a single {@link AttributeValue} back into a plain Java/JSON value. */
  public static Object toPlainValue(final AttributeValue value) {
    if (value == null) {
      return null;
    }
    return switch (value.type()) {
      case S -> value.s();
      case N -> new BigDecimal(value.n());
      case BOOL -> value.bool();
      case NUL -> null;
      case B -> value.b().asByteArray();
      case SS -> value.ss();
      case NS -> value.ns().stream().map(BigDecimal::new).toList();
      case BS -> value.bs().stream().map(SdkBytes::asByteArray).toList();
      case M -> toPlainMap(value.m());
      case L -> value.l().stream().map(AttributeValueConverter::toPlainValue).toList();
      default ->
          throw new IllegalArgumentException(
              "Unsupported DynamoDB AttributeValue type: " + value.type());
    };
  }

  /**
   * Converts an {@code AttributeValue} map into a single, merged plain {@code Map<String, Object>}
   * (e.g. as used by {@code scanTable}'s per-item shape).
   */
  public static Map<String, Object> toPlainMap(final Map<String, AttributeValue> values) {
    Map<String, Object> result = new LinkedHashMap<>();
    values.forEach((key, value) -> result.put(key, toPlainValue(value)));
    return result;
  }

  /**
   * Converts an {@code AttributeValue} map into a list of single-key maps, one per entry, in
   * iteration order -- reproducing v1's {@code Item#attributes()} quirk (an {@code
   * Iterable<Map.Entry<String,Object>>}, which Jackson serializes as an array of single-key objects
   * rather than one merged object). Used by {@code getItem} only; see {@code
   * ScanTableOperation}/{@code toPlainMap} for the merged-object shape used elsewhere.
   */
  public static List<Map<String, Object>> toSingleKeyEntries(
      final Map<String, AttributeValue> values) {
    List<Map<String, Object>> entries = new ArrayList<>();
    values.forEach((key, value) -> entries.add(Collections.singletonMap(key, toPlainValue(value))));
    return entries;
  }
}
