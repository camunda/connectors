# Element Template Generator Maven Plugin

This plugin generates [Connector Templates](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-templates/)
based on Connector code.

To use the plugin, your Connector must be annotated with `@ElementTemplate` annotation.
See the [Element Template Generator](../core) module for more information.

## Usage

Add the following plugin to your `pom.xml`:

```xml
<plugin>
  <groupId>io.camunda.connector</groupId>
  <artifactId>element-template-generator-maven-plugin</artifactId>
  <version>${version}</version>
  <executions>
    <execution>
      <goals>
        <goal>generate-templates</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <connectors>
      <connector>
        <connectorClass>io.camunda.connector.MyConnector</connectorClass>
      </connector>
    </connectors>
  </configuration>
</plugin>
```

Edit the `connectorClass` element to point to your Connector class. You can specify multiple
classes if you have more than one Connector in one project.

By default, the generated element template will be placed in `element-templates` directory inside
your project's base directory. You can change the output directory by setting the `outputDirectory`
configuration parameter.

## Accessing classes from other modules

If your Connector definition references classes from other modules, the plugin will not be able
to find them automatically. You can add direct dependencies of your project to the plugin classpath
by adding the following configuration to the plugin:

```xml
<configuration>
  <connectorClasses>
    <connectorClass>io.camunda.connector.MyConnector</connectorClass>
  </connectorClasses>
  <includeDependencies>
    <includeDependency>io.camunda.connector:my-connector-library</includeDependency>
  </includeDependencies>
</configuration>
```

Note that the plugin will not resolve transitive dependencies of the specified dependencies.

## Hybrid mode

If you want to generate an element template for a Connector that should work in Hybrid mode
(multiple runtimes running against the same Camunda cluster), you can use the
`generate-hybrid-templates` parameter:

```xml
<configuration>
  <connectors>
    <connector>
      <connectorClass>io.camunda.connector.MyConnector</connectorClass>
      <generateHybridTemplates>true</generateHybridTemplates>
    </connector>
  </connectors>
</configuration>
```

The default value is `false`.

Note that if this parameter is set to `true`, both element templates (normal one and hybrid one)
will be generated.

## Custom file name

By default, the generated element template will be stored in a file with a name derived from
the element template ID (configurable via `@ElementTemplate` annotation). You can override
the file name by setting the `templateFileName` configuration parameter:

```xml
<configuration>
  <connectorClasses>
    <connectorClass>io.camunda.connector.MyConnector</connectorClass>
    <templateFileName>my-custom-template</templateFileName>
  </connectorClasses>
</configuration>
```

In this case, the generated element template will be stored in `my-custom-template.json`.

If used together with `generateHybridTemplates`, the file name of the hybrid template will be
`my-custom-template-hybrid.json`.

If the element template is generated for multiple element types, the file name will be
`my-custom-template-<element-type>.json`. If used together with `generateHybridTemplates`, the
file name of the hybrid template will be `my-custom-template-<element-type>-hybrid.json`.
