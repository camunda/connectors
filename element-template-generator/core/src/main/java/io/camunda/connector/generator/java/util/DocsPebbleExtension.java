package io.camunda.connector.generator.java.util;

import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.util.List;
import java.util.Map;
import net.steppschuh.markdowngenerator.table.Table;

public class DocsPebbleExtension extends AbstractExtension {

  private final Map<String, Function> functions =
      Map.of(
          "props",
          new Function() {
            @Override
            public Object execute(
                Map<String, Object> args,
                PebbleTemplate self,
                EvaluationContext context,
                int lineNumber) {
              List<DocsProperty> properties = (List<DocsProperty>) args.get("properties");

              var tableBuilder =
                  new Table.Builder().addRow("Name", "Type", "Required", "Description", "Example");

              properties.forEach(
                  p -> {
                    var required = p.required() ? "Yes" : "No";
                    var exampleValueCode = "```" + p.exampleValue() + "```";
                    tableBuilder.addRow(
                        p.label(), p.type(), required, p.description(), exampleValueCode);
                  });

              return tableBuilder.build().serialize();
            }

            @Override
            public List<String> getArgumentNames() {
              return List.of("properties");
            }
          });

  @Override
  public Map<String, Function> getFunctions() {
    return functions;
  }
}
