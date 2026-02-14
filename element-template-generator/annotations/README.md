# Element Template Generator

This is a tool to generate [element templates](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-templates/)
based on the Connector Java code.

## Prerequisites

To make use of the Template Generator, your Connector must:
- Define data models for Connector inputs by means of a Java class that is used to deserialize the input JSON;
- Generally rely on the `bindVariables` method of the `OutboundConnectorContext` (and not the low-level `getVariables` method);
- Be annotated with the `@ElementTemplate` annotation defined in this module.

The points above define the minimum requirements.
You can customize and extend the functionality by using more annotations (see below).

## Usage

For most use cases, we recommend using the [Maven plugin](../maven-plugin) to invoke the Template Generator.

The Generator can be invoked directly from Java code as well. To do so, create an instance of the
`ClassBasedElementTemplateGenerator` class and invoke its `generate` method.

```java
OutboundElementTemplateGenerator generator = new OutboundElementTemplateGenerator();
OutboundElementTemplate template = generator.generate(MyConnectorFunction.class);
```

The resulting object can be serialized using Jackson (pre-configured) or any other JSON library.

## Property types

The Template Generator works out-of-the-box for most data models. If you only use the `@ElementTemplate` annotation
without any additional configuration, it will convert the Connector input data model to an Element Template
using the default rules.

| Java field type                         | Generated template property type |
|-----------------------------------------|----------------------------------|
| `String`                                | `String`                         |
| Number primitives and boxed types       | `String`                         |
| `Boolean`                               | `Boolean`                        |
| Enums                                   | `Dropdown`                       |
| Collections, Maps, `Object`, `JsonNode` | `String` with `feel: required`   |

Everything else gets converted to a `String` by default.

The property type can be customized by using the `@TemplateProperty` annotation:

```java
@TemplateProperty(type = "Text")
private String value;
```

Now the property will be of type `Text` instead of `String`.

## Property names and labels

By default, the property name and label are derived from the Java field name.
Property ID will be the same as the field name, and the label will be the field name with the first letter capitalized
and spaces inserted between words. For example, the field `myField` will be converted to a property with ID `myField`
and label `My field`.

You can customize the property name and label by using the `@TemplateProperty` annotation:

```java
@TemplateProperty(id = "myField", label = "My field")
private String value;
```

## Nested properties

The Template Generator supports nested properties. For example, if your Connector input data model looks like this:

```java
public class MyConnectorInput {
  private String name;
  private MyNestedInput nested;
}

public class MyNestedInput {
  private String value;
}
```

The generated Element Template will contain two properties:

```json
{
  "properties": [
    {
      "id": "name",
      "label": "Name",
      "binding": {
        "name": "name",
        "type": "zeebe:input"
      },
      "type": "String"
    },
    {
      "id": "nested.value",
      "label": "Value",
      "binding": {
        "name": "nested.value",
        "type": "zeebe:input"
      },
      "type": "String"
    }
  ]
}
```

As shown in the example, the property ID is composed of the field names of the nested properties
separated by a dot.
This behavior is enabled by default to prevent name clashes. You can disable it by annotating
the field that contains nested properties with `@NestedProperties(addNestedPath = false)`.

```java
@NestedProperties(addNestedPath = false)
private MyNestedInput nested;
```

This annotation also allows to apply the same condition to all nested properties of a class.
For example, if you want to add a condition for the nested properties of the `MyNestedInput`
class, you can annotate the field with `@NestedProperties` and define the condition there:

```java
import io.camunda.connector.generator.annotation.TemplateProperty.PropertyCondition;

@NestedProperties(condition = @PropertyCondition(property = "someProperty", equals = "someValue"))
private MyNestedInput nested;
```

## Sealed hierarchies

Sealed hierarchies are common for defining Connector inputs with multiple variants. For example, the
Out-of-the-Box [HTTP Connector](https://github.com/camunda/connectors/tree/main/connectors/http/rest)
uses a sealed hierarchy for different authentication methods. Refer to the simplified example below.

```java
public abstract sealed class Authentication
    permits BasicAuthentication,
        BearerAuthentication,
        CustomAuthentication,
        NoAuthentication,
        OAuthAuthentication {}

public final class BasicAuthentication extends Authentication {
  @FEEL @NotEmpty private String username;
  @FEEL @NotEmpty private String password;
}

public final class BearerAuthentication extends Authentication {
  @FEEL @NotEmpty private String token;
}
```

This technique can also be applied to define connectors with multiple operations if the inputs for the
operations are different or only partially overlapping. Another example of this is the
[AWS DynamoDB Connector](https://github.com/camunda/connectors/tree/main/connectors/aws/aws-dynamodb).

The Template Generator supports sealed hierarchies by default. For each sealed hierarchy, it generates
an additional discriminator property of type `Dropdown` that gets mapped to a `type` variable in the resulting JSON.

Nested sealed hierarchies are supported as well. The discriminator property is implicitly considered
part of the nested type.

The discriminator property can be configured by using the `@TemplateDiscriminatorProperty`.
It should be placed on the class level of the sealed hierarchy root class.

```java
@TemplateDiscriminatorProperty(name = "authenticationType", label = "Authentication type")
public abstract sealed class Authentication
    permits BasicAuthentication,
        BearerAuthentication,
        CustomAuthentication,
        NoAuthentication,
        OAuthAuthentication {}
```

Here, `name` defines the property ID and variable name of the discriminator property.

The sealed variants can be configured by using the `@TemplateSubType` annotation.
It should be placed on the class level of the sealed variant classes.

```java
@TemplateSubType(id = "basic", label = "Basic authentication")
public final class BasicAuthentication extends Authentication {
  @FEEL @NotEmpty private String username;
  @FEEL @NotEmpty private String password;
}
```

If you are relying on Jackson to deserialize the polymorphic type, make sure to align the
discriminator property name and subtype IDs with the Jackson configuration.

Note that the [nested properties rules](#nested-properties) also apply to sealed hierarchies.
The only difference is that the discriminator property is not considered part of the nested type,
so it will never be prefixed with the nested path.
Therefore, **the discriminator property ID must always be unique** within the Connector input data
model.

## Property groups

By default, if no group is defined by `@TemplateProperty`, all properties are added to the default group.
Unlike defining properties with no group at all, using a default fallback group allows to render
the properties in a better way in the Modeler.

You can configure group IDs for specific properties by using the `@TemplateProperty` annotation,
and the group labels can be customized in the `@ElementTemplate` annotation:

```java
  @ElementTemplate(
      id = "myConnector",
      name = "My Connector",
      version = 1,
      propertyGroups = {
        @PropertyGroup(id = "group2", label = "Group Two"),
        @PropertyGroup(id = "group1", label = "Group One")
      })
    public class MyConnectorFunction { }
```

The order of the groups is also determined by the order of the `@PropertyGroup` annotations.
In the example above, the `Group Two` will be rendered before `Group One`.

## Property validation

The Template Generator allows to define validation constraints for properties.
Validation constraints can be defined using the standard Bean Validation annotations.

```java
@NotEmpty
private String value;
```

The property above will receive a `notEmpty` constraint in the generated element template.

The following Bean Validation annotations are supported:
- `@NotEmpty`
- `@NotBlank` for strings, results in a `notEmpty` constraint
- `@NotNull` for objects, results in a `notEmpty` constraint
- `@Size` for strings, results in a `minLength` and `maxLength` constraint
- `@Pattern`

## Additional default properties

The Template Generator adds additional default properties to the generated Element Template. These
properties are not part of the Connector input data model, but are required by the Connector Runtime
to execute the Connector. The following properties are added by default.

### Outbound connectors
- `errorExpression` - Expression that is evaluated to determine if the Connector invocation failed.
- `resultVariable` - Name of the variable that is used to store the Connector invocation result.
- `resultExpression` - Expression that is evaluated to determine the Connector invocation result.

### Inbound connectors
- `resultVariable` - Name of the variable that is used to store the Connector invocation result.
- `resultExpression` - Expression that is evaluated to determine the Connector invocation result.
- `activationCondition` - Expression that is evaluated to determine if the Connector should be invoked.
- `correlationKeyExpression` - Expression that is evaluated to determine the correlation key from the inbound message payload.
- `correlationKey` - Message correlation key, compared against the `correlationKeyExpression` result during message correlation.
- `messageIdExpression` - Expression that is evaluated to determine the message ID from the process instance variables.

Message-related properties are only added to templates that target element types which contain messages,
like message start events or message catch events.

## Property binding

Every generated property is bound to a Zeebe input (`zeebe:input` mapping). The binding name is derived from the
field name. Other bindings, like task headers, are currently not supported by the `@TemplateProperty` annotation.

## Icons

A custom element template icon can be defined by using the `@ElementTemplate` annotation:

```java
@ElementTemplate(
    id = "myConnector",
    name = "My Connector",
    version = 1,
    icon = "my-connector.svg")
public class MyConnectorFunction { }
```

You can use SVG or PNG graphics for the icon, although SVG is recommended. The icons get rendered
18x18 pixels in the element on the modeling canvas, and 32x32 pixels in the properties panel.

The icon file must be available as a resource in the classpath. By default, it is expected to be in the
`src/main/resources` directory.

When running in a multi-module Maven environment using the
[Maven Plugin](../maven-plugin), the resources of a connector module are
not visible to the Template Generator's default class loader. To mitigate this, the Maven Plugin
adds the individual connector resources to the custom class loader that can be consumed by the
Template Generator either via `Thread.currentThread().getContextClassLoader()` or directly via
constructor injection.

## Element Template DSL

This module defines a DSL for building element templates programmatically. The starting point is the
`ElementTemplate` class. You can use the DSL directly to build the template and then invoke
the `build` method. The resulting `ElementTemplate` object can be serialized to JSON using Jackson
(pre-configured) or any other JSON library (would require custom configuration).
