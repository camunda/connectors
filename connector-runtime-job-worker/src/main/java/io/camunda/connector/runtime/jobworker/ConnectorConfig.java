package io.camunda.connector.runtime.jobworker;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

class ConnectorConfig {

  public static final Pattern ZEEBE_CONNECTOR_PATTERN =
      Pattern.compile("^ZEEBE_CONNECTOR_([^_]+)_TYPE");

  public static List<ConnectorConfig> parse() {

    var connectors = new ArrayList<ConnectorConfig>();

    for (var entry : System.getenv().entrySet()) {

      var key = entry.getKey();

      var match = ZEEBE_CONNECTOR_PATTERN.matcher(key);

      if (match.matches()) {
        connectors.add(parseConnector(match.group(1)));
      }
    }

    return connectors;
  }

  private static ConnectorConfig parseConnector(String name) {

    var type = getEnv(name, "TYPE");
    var function = getEnv(name, "FUNCTION");
    var variables = getEnv(name, "VARIABLES").split(",");

    return new ConnectorConfig(name, type, variables, function);
  }

  static String getEnv(String name, String detail) {
    return System.getenv("ZEEBE_CONNECTOR_" + name + "_" + detail);
  }

  private String name;
  private String type;
  private final String[] variables;
  private String function;

  public ConnectorConfig(String name, String type, String[] variables, String className) {
    this.name = name;
    this.type = type;
    this.variables = variables;
    this.function = className;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public String getFunction() {
    return function;
  }

  public String[] getVariables() {
    return variables;
  }
}
