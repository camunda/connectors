# Element Template Generator Maven Plugin

This plugin generates [Connector Templates](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-templates/)
based on Connector code.

To use the plugin, your Connector must be annotated with `@ElementTemplate` annotation.
See the [Element Template Generator](../element-template-generator) module for more information.

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
    <connectorClasses>
      <connectorClass>io.camunda.connector.MyConnector</connectorClass>
    </connectorClasses>
  </configuration>
</plugin>
```

Edit the `connectorClass` element to point to your Connector class. You can specify multiple
classes if you have more than one Connector in one project.

The generated element template will be placed in `element-templates` directory in your project.

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
