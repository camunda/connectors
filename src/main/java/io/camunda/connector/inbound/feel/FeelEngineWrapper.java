package io.camunda.connector.inbound.feel;

import org.camunda.feel.FeelEngine;
import org.camunda.feel.impl.SpiServiceLoader;
import org.springframework.stereotype.Service;
import scala.jdk.javaapi.CollectionConverters;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class FeelEngineWrapper {

  private final FeelEngine feelEngine;

  public FeelEngineWrapper() {
    feelEngine =
        new FeelEngine.Builder()
            .valueMapper(SpiServiceLoader.loadValueMapper())
            .functionProvider(SpiServiceLoader.loadFunctionProvider())
            .build();
  }

  private static String trimExpression(final String expression) {
    var feelExpression = expression.trim();
    if (feelExpression.startsWith("=")) {
      feelExpression = feelExpression.substring(1);
    }
    return feelExpression.trim();
  }

  private static scala.collection.immutable.Map<String, Object> toScalaMap(
      final Map<String, Object> map) {
    final HashMap<String, Object> context = new HashMap<>(map);
    return scala.collection.immutable.Map.from(CollectionConverters.asScala(context));
  }

  private static Object toJava(final Object scalaObject) {

    if (scalaObject instanceof scala.collection.immutable.Map) {
      return CollectionConverters.asJava((scala.collection.immutable.Map) scalaObject);
    }

    return scalaObject;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ensureVariablesMap(final Object variables) {
    return (Map<String, Object>) Objects.requireNonNull(variables, "variables cannot be null");
  }

  public <T> T evaluate(final String expression, final Object variables) {
    scala.collection.immutable.Map<String, Object> context =
        Optional.ofNullable(variables)
            .map(FeelEngineWrapper::ensureVariablesMap)
            .map(FeelEngineWrapper::toScalaMap)
            .get();

    var result = feelEngine.evalExpression(trimExpression(expression), context);

    if (result.isLeft()) {
      throw new FeelEngineWrapperException(
          "expression evaluation failed with message: " + result.left().get().message(),
          expression,
          variables);
    }

    var val = result.right().get();

    if (val == null) {
      return null;
    }

    return (T) toJava(val);
  }
}
