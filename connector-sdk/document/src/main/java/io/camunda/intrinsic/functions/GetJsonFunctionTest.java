package io.camunda.intrinsic.functions;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.Document;
import io.camunda.document.reference.InMemoryDocumentReference;
import org.junit.jupiter.api.Test;

public class GetJsonFunctionTest {

  private final GetJsonFunction function = new GetJsonFunction();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldReturnWholeJsonIfNoExpression() {
    String json = "{\"foo\":123,\"bar\":\"baz\"}";
    Document doc = new InMemoryDocumentReference(json.getBytes()).resolve();
    Object result = function.execute(doc, null);
    assertEquals(123, ((java.util.Map<?,?>)result).get("foo"));
    assertEquals("baz", ((java.util.Map<?,?>)result).get("bar"));
  }

  @Test
  void shouldReturnPartOfJsonWithFeelExpression() {
    String json = "{\"foo\":123,\"bar\":\"baz\"}";
    Document doc = new InMemoryDocumentReference(json.getBytes()).resolve();
    Object result = function.execute(doc, "foo");
    assertEquals(123, result);
  }
}
