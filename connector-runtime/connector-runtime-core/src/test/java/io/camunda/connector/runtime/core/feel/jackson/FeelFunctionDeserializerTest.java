package io.camunda.connector.runtime.core.feel.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class FeelFunctionDeserializerTest {

  private final ObjectMapper mapper = new ObjectMapper()
      .registerModule(new JacksonModuleFeel());

  @Test
  void feelFunctionDeserialization_objectResult() throws JsonProcessingException {
    // given
    String json = """
        { "function": "= { result: a + b }" }
        """;

    // when
    TargetTypeObject targetType = mapper.readValue(json, TargetTypeObject.class);

    // then
    InputContextString inputContext = new InputContextString("foo", "bar");
    Object result = targetType.function().apply(inputContext);
    assertThat(result).isInstanceOf(OutputContext.class);
    OutputContext outputContext = (OutputContext) result;
    assertThat(outputContext.result).isEqualTo("foobar");
  }

  @Test
  void feelFunctionDeserialization_stringResult() throws JsonProcessingException {
    // given
    String json = """
        { "function": "= a + b" }
        """;

    // when
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then
    InputContextString inputContext = new InputContextString("foo", "bar");
    String result = targetType.function().apply(inputContext);
    assertThat(result).isEqualTo("foobar");
  }

  @Test
  void feelFunctionDeserialization_booleanResult() throws JsonProcessingException {
    // given
    String json = """
        { "function": "= a = b" }
        """;

    // when
    TargetTypeBoolean targetType = mapper.readValue(json, TargetTypeBoolean.class);

    // then
    InputContextString inputContext = new InputContextString("foo", "bar");
    Boolean result = targetType.function().apply(inputContext);
    assertThat(result).isFalse();
  }

  @Test
  void feelFunctionDeserialization_integerResult() throws JsonProcessingException {
    // given
    String json = """
        { "function": "= a + b" }
        """;

    // when
    TargetTypeInteger targetType = mapper.readValue(json, TargetTypeInteger.class);

    // then
    InputContextInteger inputContext = new InputContextInteger(3, 5);
    Integer result = targetType.function().apply(inputContext);
    assertThat(result).isEqualTo(8);
  }

  record InputContextString(String a, String b) {}

  record InputContextInteger(Integer a, Integer b) {}

  record OutputContext(String result) {}

  record TargetTypeObject(Function<InputContextString, OutputContext> function) {}

  record TargetTypeString(Function<InputContextString, String> function) {}

  record TargetTypeBoolean(Function<InputContextString, Boolean> function) {}

  record TargetTypeInteger(Function<InputContextInteger, Integer> function) {}
}
