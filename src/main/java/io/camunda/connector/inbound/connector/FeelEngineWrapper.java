package io.camunda.connector.inbound.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.scala.DefaultScalaModule$;
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
  private final ObjectMapper objectMapper;

  public FeelEngineWrapper() {
    feelEngine =
        new FeelEngine.Builder()
            .valueMapper(SpiServiceLoader.loadValueMapper())
            .functionProvider(SpiServiceLoader.loadFunctionProvider())
            .build();
    objectMapper = new ObjectMapper().registerModule(DefaultScalaModule$.MODULE$);
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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ensureVariablesMap(final Object variables) {
    return (Map<String, Object>) Objects.requireNonNull(variables, "variables cannot be null");
  }

  public <T> T evaluate(final String expression, final Object variables) {
    return Optional.ofNullable(variables)
        .map(FeelEngineWrapper::ensureVariablesMap)
        .map(FeelEngineWrapper::toScalaMap)
        .map(context -> feelEngine.evalExpression(trimExpression(expression), context))
        .map(
            evaluationResult -> {

              // throw error on evaluation issue
              evaluationResult
                  .left()
                  .foreach(
                      failure -> {
                        throw new FeelEngineWrapperException(
                            "expression evaluation failed with message: " + failure.message(),
                            expression,
                            variables);
                      });

              return (T) evaluationResult.right().get();
            })
        .get();
  }
}
