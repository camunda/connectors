package io.camunda.document.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import javax.annotation.Nullable;

public class IntrinsicOperationParameterBinder {

  private final ObjectMapper objectMapper;

  public IntrinsicOperationParameterBinder(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Object[] bindParameters(Method method, IntrinsicOperationParams params) {
    var parameterTypes = method.getParameterTypes();
    var parameterCount = parameterTypes.length;
    var parameterAnnotations = method.getParameterAnnotations();
    var parameterValues = new Object[parameterCount];

    for (var i = 0; i < parameterCount; i++) {
      var parameter = method.getParameters()[i];
      var parameterType = parameterTypes[i];
      var parameterAnnotationsList = List.of(parameterAnnotations[i]);
      parameterValues[i] = bindParameter(parameter, parameterType, parameterAnnotationsList, i,
          params);
    }
    return parameterValues;
  }

  private Object bindParameter(
      Parameter parameter,
      Class<?> type,
      List<Annotation> annotations,
      int index,
      IntrinsicOperationParams params) {

    if (params instanceof IntrinsicOperationParams.Positional positionalParams) {
      if (index >= positionalParams.params().size()) {
        if (annotations.stream().noneMatch(a -> a instanceof Nullable)) {
          throw new IllegalArgumentException(
              "Parameter at index " + index + " is required but not provided");
        }
        return null;
      }

      try {
        return objectMapper.convertValue(positionalParams.params().get(index), type);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Failed to convert parameter " + parameter.getName() + " at index " + index
                + " to type " + type, e);
      }
    } else {
      throw new IllegalArgumentException("Unsupported parameter type: " + params);
    }
  }
}
