/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.SdkNumber;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.document.DocumentVisitor;
import software.amazon.awssdk.core.protocol.MarshallingKnownType;
import software.amazon.awssdk.core.traits.ListTrait;
import software.amazon.awssdk.core.traits.MapTrait;
import software.amazon.awssdk.utils.builder.Buildable;

/**
 * Generic bidirectional codec between AWS SDK v2 {@link SdkPojo} instances (as generated for {@code
 * bedrockruntime} model classes such as {@code ContentBlock}) and plain {@code Map<String,Object>}
 * structures.
 *
 * <p><strong>Why generic instead of typed per-type mapping.</strong> {@code ContentBlock} is a
 * 15-member union; beyond {@code text}, {@code toolUse} and {@code reasoningContent}, the remaining
 * members ({@code citationsContent} and friends) drag in 15-20 nested types across recursive unions
 * ({@code Citation} &rarr; {@code CitationSourceContent}, {@code CitationLocation}, {@code
 * SearchResultContentBlock}, {@code CitationsConfig}, ...). Walking {@link SdkPojo#sdkFields()}
 * reflectively is far smaller than hand-writing a converter per type, stays correct across AWS SDK
 * version bumps that add fields or members, and doubles as the residual-metadata mechanism for
 * ordinary {@code text} and {@code toolUse} blocks (see the design spec &sect;5.4 for the full
 * rationale).
 *
 * <p><strong>Capture direction ({@link #capture(SdkPojo)}).</strong> Walks {@code
 * pojo.sdkFields()}; for every field with a non-null value ({@link
 * SdkField#getValueOrDefault(Object)}), keys the result by {@link SdkField#locationName()} and
 * converts the value according to {@link SdkField#marshallingType()}: {@code SDK_POJO} recurses
 * into {@link #capture(SdkPojo)}; {@code LIST}/{@code MAP} recurse element-by-element using the
 * member field described by {@link ListTrait#memberFieldInfo()} / {@link
 * MapTrait#valueFieldInfo()}; {@code SDK_BYTES} is base64-encoded; {@code DOCUMENT} is converted to
 * a plain Java value tree (see {@link #captureDocument(Document)}); {@code INSTANT} becomes an
 * ISO-8601 string; everything else (including enum-backed fields, which the AWS SDK always models
 * as {@code STRING} at the {@link SdkField} level - see {@code ToolUseBlock#typeAsString()}) is
 * copied as-is.
 *
 * <p><strong>Replay direction ({@link #replay(Map, Supplier)}).</strong> The inverse walk: a
 * builder is obtained from the supplied {@link Supplier}, and for every {@link SdkField} of that
 * builder whose {@link SdkField#locationName()} is present in the captured map, the raw value is
 * coerced back by the same {@code marshallingType()} switch and applied via {@link
 * SdkField#set(Object, Object)}. Nested {@code SDK_POJO} values are rebuilt through {@link
 * SdkField#constructor()} (despite the name, a {@code Supplier<SdkPojo>} that returns a
 * <em>builder</em>, per its javadoc) and then built via {@link Buildable#build()} - every AWS SDK
 * v2 generated builder implements both {@link SdkPojo} (so fields can be set on it) and, through
 * {@code CopyableBuilder}/{@code SdkBuilder}, {@link Buildable} (so it can be turned into the
 * immutable POJO).
 *
 * <p>Only {@code ContentBlock.Type.UNKNOWN_TO_SDK_VERSION} cannot be round-tripped through this
 * codec, because the AWS SDK surfaces no field data for it at all; every other member - typed or
 * not - captures and replays faithfully.
 */
final class BedrockSdkPojoCodec {

  private BedrockSdkPojoCodec() {}

  /**
   * Captures every non-null field of {@code pojo} into a plain {@code Map<String,Object>}, keyed by
   * {@link SdkField#locationName()} and recursively converted per {@link
   * SdkField#marshallingType()}.
   */
  static Map<String, Object> capture(SdkPojo pojo) {
    final Map<String, Object> captured = new LinkedHashMap<>();
    for (final SdkField<?> field : pojo.sdkFields()) {
      final Object value = field.getValueOrDefault(pojo);
      if (value != null) {
        captured.put(field.locationName(), captureValue(value, field));
      }
    }
    return captured;
  }

  /**
   * Replays a map produced by {@link #capture(SdkPojo)} back into an {@link SdkPojo}.
   *
   * @param builderSupplier supplies the (empty) builder to populate, e.g. {@code
   *     ContentBlock::builder}. AWS SDK v2 builders implement both {@link SdkPojo} (for {@link
   *     SdkField#set(Object, Object)}) and {@link Buildable} (for the final {@link
   *     Buildable#build()} call), which this method relies on via a runtime cast.
   */
  static <T extends SdkPojo> T replay(
      Map<String, Object> captured, Supplier<SdkPojo> builderSupplier) {
    @SuppressWarnings("unchecked")
    final T result = (T) replayInto(captured, builderSupplier);
    return result;
  }

  private static SdkPojo replayInto(
      Map<String, Object> captured, Supplier<SdkPojo> builderSupplier) {
    final SdkPojo builder = builderSupplier.get();
    for (final SdkField<?> field : builder.sdkFields()) {
      final String key = field.locationName();
      if (captured.containsKey(key)) {
        final Object rawValue = captured.get(key);
        field.set(builder, rawValue == null ? null : replayValue(rawValue, field));
      }
    }
    if (!(builder instanceof Buildable buildable)) {
      throw new IllegalStateException(
          "Builder "
              + builder.getClass().getName()
              + " does not implement "
              + Buildable.class.getName()
              + "; cannot build() it generically");
    }
    return (SdkPojo) buildable.build();
  }

  // -- value-level capture/replay, shared between top-level fields and LIST/MAP elements --

  private static @Nullable Object captureValue(Object value, SdkField<?> field) {
    final MarshallingKnownType knownType = field.marshallingType().getKnownType();
    if (knownType == null) {
      // Marshalling type unknown to this codec (e.g. a future AWS SDK addition) - carry the raw
      // value through unconverted rather than dropping it.
      return value;
    }
    return switch (knownType) {
      case SDK_POJO -> capture((SdkPojo) value);
      case LIST -> captureList((List<?>) value, listElementField(field));
      case MAP -> captureMap((Map<?, ?>) value, mapValueField(field));
      case SDK_BYTES -> Base64.getEncoder().encodeToString(((SdkBytes) value).asByteArray());
      case DOCUMENT -> captureDocument((Document) value);
      case INSTANT -> ((Instant) value).toString();
      default -> value; // STRING (incl. enum-backed fields), numeric types, BOOLEAN, ...
    };
  }

  private static Object replayValue(Object value, SdkField<?> field) {
    final MarshallingKnownType knownType = field.marshallingType().getKnownType();
    if (knownType == null) {
      return value;
    }
    return switch (knownType) {
      case SDK_POJO -> {
        @SuppressWarnings("unchecked")
        final Map<String, Object> nested = (Map<String, Object>) value;
        yield replayInto(nested, field.constructor());
      }
      case LIST -> replayList((List<?>) value, listElementField(field));
      case MAP -> {
        @SuppressWarnings("unchecked")
        final Map<String, Object> nested = (Map<String, Object>) value;
        yield replayMap(nested, mapValueField(field));
      }
      case SDK_BYTES -> SdkBytes.fromByteArray(Base64.getDecoder().decode((String) value));
      case DOCUMENT -> replayDocument(value);
      case INSTANT -> Instant.parse((String) value);
      default -> value;
    };
  }

  private static List<Object> captureList(List<?> values, SdkField<?> elementField) {
    final List<Object> captured = new ArrayList<>(values.size());
    for (final Object element : values) {
      captured.add(element == null ? null : captureValue(element, elementField));
    }
    return captured;
  }

  private static List<Object> replayList(List<?> values, SdkField<?> elementField) {
    final List<Object> replayed = new ArrayList<>(values.size());
    for (final Object element : values) {
      replayed.add(element == null ? null : replayValue(element, elementField));
    }
    return replayed;
  }

  private static Map<String, Object> captureMap(Map<?, ?> values, SdkField<?> valueField) {
    final Map<String, Object> captured = new LinkedHashMap<>();
    values.forEach(
        (key, value) ->
            captured.put((String) key, value == null ? null : captureValue(value, valueField)));
    return captured;
  }

  private static Map<String, Object> replayMap(Map<String, Object> values, SdkField<?> valueField) {
    final Map<String, Object> replayed = new LinkedHashMap<>();
    values.forEach(
        (key, value) -> replayed.put(key, value == null ? null : replayValue(value, valueField)));
    return replayed;
  }

  @SuppressWarnings("rawtypes")
  private static SdkField<?> listElementField(SdkField<?> field) {
    final ListTrait listTrait = field.getTrait(ListTrait.class);
    if (listTrait == null) {
      throw new IllegalStateException(
          "LIST field '" + field.locationName() + "' is missing its ListTrait");
    }
    return listTrait.memberFieldInfo();
  }

  @SuppressWarnings("rawtypes")
  private static SdkField<?> mapValueField(SdkField<?> field) {
    final MapTrait mapTrait = field.getTrait(MapTrait.class);
    if (mapTrait == null) {
      throw new IllegalStateException(
          "MAP field '" + field.locationName() + "' is missing its MapTrait");
    }
    return mapTrait.valueFieldInfo();
  }

  // -- AWS Document <-> plain Java value tree --
  //
  // Document.unwrap() cannot be reused here: it collapses numbers to their string representation
  // (NumberDocument.unwrap() returns SdkNumber#stringValue()), which is indistinguishable from a
  // genuine string once captured. Since NumberDocument#equals()/StringDocument#equals() both
  // require the other side to be an instance of the *same* concrete Document subtype, replaying a
  // captured number as a string (or vice versa) would silently break round-trip equality.
  // BigDecimal
  // is therefore used as the plain-Java stand-in for a number: it is distinct from String, and
  // SdkNumber#equals()/#hashCode() are defined purely in terms of #stringValue() (see
  // SdkNumber.java), so any BigDecimal whose toString() reproduces the original stringValue()
  // round-trips losslessly regardless of which of SdkNumber's typed factory methods (fromInteger,
  // fromDouble, ...) originally produced it.

  private static @Nullable Object captureDocument(Document document) {
    return document.accept(
        new DocumentVisitor<@Nullable Object>() {
          @Override
          public @Nullable Object visitNull() {
            return null;
          }

          @Override
          public Object visitBoolean(Boolean value) {
            return value;
          }

          @Override
          public Object visitString(String value) {
            return value;
          }

          @Override
          public Object visitNumber(SdkNumber value) {
            return new BigDecimal(value.stringValue());
          }

          @Override
          public Object visitMap(Map<String, Document> value) {
            final Map<String, Object> result = new LinkedHashMap<>();
            value.forEach((key, nested) -> result.put(key, captureDocument(nested)));
            return result;
          }

          @Override
          public Object visitList(List<Document> value) {
            final List<Object> result = new ArrayList<>(value.size());
            for (final Document nested : value) {
              result.add(captureDocument(nested));
            }
            return result;
          }
        });
  }

  private static Document replayDocument(@Nullable Object value) {
    if (value == null) {
      return Document.fromNull();
    } else if (value instanceof Boolean bool) {
      return Document.fromBoolean(bool);
    } else if (value instanceof String string) {
      return Document.fromString(string);
    } else if (value instanceof BigDecimal number) {
      return Document.fromNumber(number);
    } else if (value instanceof Map<?, ?> map) {
      final Map<String, Document> result = new LinkedHashMap<>();
      map.forEach((key, nested) -> result.put((String) key, replayDocument(nested)));
      return Document.fromMap(result);
    } else if (value instanceof List<?> list) {
      final List<Document> result = new ArrayList<>(list.size());
      for (final Object nested : list) {
        result.add(replayDocument(nested));
      }
      return Document.fromList(result);
    }
    throw new IllegalArgumentException(
        "Cannot replay a Document from value of type " + value.getClass().getName());
  }
}
