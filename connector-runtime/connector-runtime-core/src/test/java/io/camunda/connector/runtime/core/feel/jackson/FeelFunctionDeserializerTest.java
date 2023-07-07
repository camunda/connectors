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
  void feelFunctionDeserialization() throws JsonProcessingException {
    // given
    String json = """
        { "concatenate": "= { result: a + b }" }
        """;

    // when
    TargetType targetType = mapper.readValue(json, TargetType.class);

    // then
    InputContext inputContext = new InputContext("foo", "bar");
    String result = targetType.concatenate().apply(inputContext);
    assertThat(result).isEqualTo("foobar");
  }

  record TargetType(Function<InputContext, String> concatenate) {}

  record InputContext(String a, String b) {}

  record OutputContext(String result) {}
}
