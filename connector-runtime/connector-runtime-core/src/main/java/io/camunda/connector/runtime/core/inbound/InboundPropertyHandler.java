package io.camunda.connector.runtime.core.inbound;

import io.camunda.connector.impl.Constants;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InboundPropertyHandler {

  /**
   * Converts a map of properties with keys containing dots into a nested map. <p>Example:
   * <pre>{"foo.alpha": "a", "foo.beta": "b"}</pre>
   * will be converted to
   * <pre>{"foo": {"alpha": "a", "beta": "b"}}</pre>
   * <p>This is needed to convert inbound connector properties into a format similar to the one used
   * by Zeebe to provide the outbound connector variables. Thanks to this conversion, the Runtime
   * can handle inbound and outbound connectors in a similar way.
   * <p>
   * NOTE: Reserved property with the name 'inbound.type' is ignored by this method and kept as is.
   *
   * @param unwrappedProperties map of properties with composite keys containing dots
   * @return nested map of properties
   */
  public static Map<String, Object> readWrappedProperties(Map<String, String> unwrappedProperties) {

    // disassemble keys with dots and store paths in map
    // e.g. {"foo.bar": "baz"} -> {["foo", "bar"]: "baz"}
    Map<List<String>, String> pathMap = unwrappedProperties.entrySet().stream().collect(
        Collectors.toMap(
            entry -> splitKeyIgnoringReservedKeys(entry.getKey()),
            Map.Entry::getValue));

    // traverse map of paths and assemble the nested map
    var wrapped = new HashMap<String, Object>();
    try {
      for (var entry : pathMap.entrySet()) {
        var path = entry.getKey();
        var value = entry.getValue();

        traverse(path, wrapped, value);
      }
    } catch (RuntimeException e) {
      throw new RuntimeException("Detected malformed Connector properties: " + e.getMessage(), e);
    }
    return wrapped;
  }

  private static List<String> splitKeyIgnoringReservedKeys(String key) {
    // we don't want to transform 'inbound.type' property for simplicity of handling
    if (Constants.RESERVED_KEYWORDS.contains(key)) {
      return List.of(key);
    }
    return Arrays.asList(key.split("\\."));
  }

  @SuppressWarnings("unchecked")
  private static void traverse(List<String> path, Map<String, Object> currentRoot, String value) {

    String key = path.get(0);
    if (path.size() == 1) {
      if (currentRoot.containsKey(key)) {
        // Node is expected to be a unique terminal node, but something is already stored
        throw new RuntimeException("Duplicate key: " + key);
      }
      currentRoot.put(key, value);
      return;
    }
    if (key.isBlank()) {
      // Empty key is not allowed
      throw new RuntimeException("Empty key in path: " + path);
    }

    var subTree = currentRoot.get(key);

    if (subTree == null) {
      var newSubTree = new HashMap<String, Object>();
      currentRoot.put(key, newSubTree);
    } else if (!(subTree instanceof Map)) {
      // Terminal node already exists for this path
      throw new RuntimeException("Duplicate key: " + key);
    }
    traverse(
        path.subList(1, path.size()),
        (Map<String, Object>) currentRoot.get(key),
        value);
  }
}
