package io.camunda.connector.runtime.core.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class InboundPropertyHandlerTest {

  @Test
  void valid_1_level_properties() {
    // given
    var properties = Map.of(
        "foo", "bar",
        "baz", "bar");

    // when
    var result = InboundPropertyHandler.readWrappedProperties(properties);

    // then
    assertThat(result).isEqualTo(properties);
  }

  @Test
  void valid_2_level_properties() {
    // given
    var properties = Map.of(
        "foo.bar", "baz",
        "foo.baz", "bar");

    // when
    var result = InboundPropertyHandler.readWrappedProperties(properties);

    // then
    var expected = Map.of(
        "foo", Map.of(
            "bar", "baz",
            "baz", "bar"));
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void valid_3_level_properties() {
    // given
    var properties = Map.of(
        "foo.bar.baz", "baz",
        "foo.bar.baz2", "baz2",
        "foo.baz", "bar");

    // when
    var result = InboundPropertyHandler.readWrappedProperties(properties);

    // then
    var expected = Map.of(
        "foo", Map.of(
            "bar", Map.of(
                "baz", "baz",
                "baz2", "baz2"),
            "baz", "bar"));
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void valid_mixed_level_properties() {
    // given
    var properties = Map.of(
        "foo", "baz",
        "bar.baz", "baz");

    // when
    var result = InboundPropertyHandler.readWrappedProperties(properties);

    // then
    var expected = Map.of(
        "foo", "baz",
        "bar", Map.of("baz", "baz"));
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void valid_withTrailingDot_noEmpty() {
    // given
    var properties = Map.of(
        "bar.baz.", "baz");

    // when
    var result = InboundPropertyHandler.readWrappedProperties(properties);

    // then
    var expected = Map.of(
        "bar", Map.of("baz", "baz"));
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void valid_inboundTypeKeyword_isIgnored() {
    // given
    var properties = Map.of(
        "bar.baz.", "baz",
        "inbound.type", "io.camunda:webhook:1");

    // when
    var result = InboundPropertyHandler.readWrappedProperties(properties);

    // then
    var expected = Map.of(
        "bar", Map.of("baz", "baz"),
        "inbound.type", "io.camunda:webhook:1");
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void invalid_duplicateKey_shorterFirst() {
    // given
    var properties = Map.of(
        "foo.bar", "baz",
        "foo.bar.baz", "baz");

    // when
    Supplier<Map<String, Object>> getResultLambda = () ->
        InboundPropertyHandler.readWrappedProperties(properties);

    // then
    assertThrows(RuntimeException.class, getResultLambda::get);
  }

  @Test
  void invalid_duplicateKey_longerFirst() {
    // given
    var properties = Map.of(
        "foo.bar.baz", "baz",
        "foo.bar", "baz");

    // when
    Supplier<Map<String, Object>> getResultLambda = () ->
        InboundPropertyHandler.readWrappedProperties(properties);

    // then
    assertThrows(RuntimeException.class, getResultLambda::get);
  }

  @Test
  void invalid_emptyPathPart() {
    // given
    var properties = Map.of(
        "foo..bar", "baz");

    // when
    Supplier<Map<String, Object>> getResultLambda = () ->
        InboundPropertyHandler.readWrappedProperties(properties);

    // then
    assertThrows(RuntimeException.class, getResultLambda::get);
  }
}
