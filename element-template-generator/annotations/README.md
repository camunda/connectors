# Element Template Generator Annotations

This module (`element-template-generator-annotations`) contains the annotations that drive the
[element template](https://docs.camunda.io/docs/8.10/components/connectors/custom-built-connectors/connector-templates/)
generation from Connector Java code. It is the single reference for the annotation DSL.
For the generator itself (invocation, default properties, programmatic DSL), see the
[core module](../core).

```xml
<dependency>
  <groupId>io.camunda.connector</groupId>
  <artifactId>element-template-generator-annotations</artifactId>
</dependency>
```

All annotations live in the package `io.camunda.connector.generator.java.annotation`.

## Annotation overview

| Annotation / type                                                | Target                            | Purpose                                                                      |
|------------------------------------------------------------------|-----------------------------------|------------------------------------------------------------------------------|
| [`@ElementTemplate`](#elementtemplate)                           | Connector class                   | Enables template generation and defines template-level metadata              |
| [`@TemplateProperty`](#templateproperty)                         | Field, parameter, record component | Customizes a single generated property                                      |
| [`@DropdownItem`](#dropdownitem)                                 | Enum constant                     | Customizes label and order of a dropdown choice                              |
| [`FeelMode`](#feelmode)                                          | (enum, used by `@TemplateProperty`) | FEEL support of a property                                                 |
| [`@NestedProperties`](#nestedproperties)                         | Field, record component           | Controls nested property paths, conditions and groups                        |
| [`@TemplateDiscriminatorProperty`](#templatediscriminatorproperty) | Sealed hierarchy root           | Customizes the discriminator property of a sealed hierarchy                  |
| [`@TemplateSubType`](#templatesubtype)                           | Sealed hierarchy variant          | Customizes a variant of a sealed hierarchy                                   |
| [`@TemplateDocumentProperty`](#templatedocumentproperty)         | Field, parameter, record component | Generates a document input (source dropdown + FEEL composer)               |
| [`DocumentSource`](#documentsource)                              | (enum, used by `@TemplateDocumentProperty`) | Sources a document input can be supplied from                      |
| [`FieldVisibility`](#fieldvisibility)                            | (enum, used by document annotations) | Visibility of generated document sub-properties                           |
| [`@DocumentReturnFormat`](#documentreturnformat)                 | Input class or sealed variant     | Generates a "Response format" dropdown for document-returning connectors     |
| [`BpmnType`](#bpmntype)                                          | (enum, used by `@ElementTemplate`) | BPMN element types a template can apply to / be turned into                 |
| [`@DataExample`](#dataexample)                                   | Static method of an output class  | Provides an example result shown in the result expression tooltip            |
| [`@TemplateLinkedResource`](#templatelinkedresource)             | Request class (repeatable)        | Generates a `zeebe:linkedResource` block (e.g. a form)                       |

## Prerequisites

To make use of the Template Generator, your Connector must:
- Define data models for Connector inputs by means of a Java class that is used to deserialize the input JSON;
- Generally rely on the `bindVariables` method of the `OutboundConnectorContext` (and not the low-level `getVariables` method);
- Be annotated with the `@ElementTemplate` annotation defined in this module.

The points above define the minimum requirements.
You can customize and extend the functionality by using more annotations (see below).

## ElementTemplate

`@ElementTemplate` enables template generation for a Connector. It is placed on the Connector class.

```java
@ElementTemplate(
    id = "io.camunda.connector.MyConnector.v1",
    name = "My Connector",
    version = 1,
    inputDataClass = MyConnectorInput.class,
    description = "Does something useful",
    documentationRef = "https://docs.camunda.io/docs/8.10/components/connectors/out-of-the-box-connectors/my-connector/",
    icon = "my-connector.svg")
public class MyConnectorFunction implements OutboundConnectorFunction { }
```

| Attribute                 | Required | Default                                  | Description                                                                                                                                                                  |
|---------------------------|----------|------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`                      | Yes      | —                                        | Element template ID. Must be unique among the connector templates used in your project.                                                                                      |
| `name`                    | Yes      | —                                        | Element template name, displayed in Camunda Modeler.                                                                                                                         |
| `inputDataClass`          | No       | `{}`                                     | Connector input data class(es). The template is generated from their properties, merged in declaration order. A single class can be given without braces.                    |
| `outputDataClass`         | No       | `Void.class`                             | Connector output data class. Used to look up a [`@DataExample`](#dataexample) for the result expression tooltip.                                                              |
| `extensionProperties`     | No       | `{}`                                     | `@ExtensionProperty(name, value, condition)` entries added to the template as hidden properties.                                                                             |
| `configurations`          | No       | `{}`                                     | Classes annotated with `@Configuration` whose templates are embedded in the generated template. All model fields are included, `@TemplateProperty` only customizes them. A non-empty list requires an `engineVersion` with a lower bound of at least 8.10, otherwise generation fails. |
| `version`                 | No       | `0`                                      | Template version. Increment whenever a non-cosmetic change is shipped (e.g. new properties, a changed icon or changed default bindings) to make use of the Modeler's version upgrade mechanism. Set it explicitly for production templates.                              |
| `category`                | No       | `@Category(id = "connectors", name = "Connectors")` | Template category, see [Category](#category).                                                                                                               |
| `documentationRef`        | No       | `""`                                     | Link to the documentation page. If empty, the Modeler does not display the documentation button.                                                                             |
| `engineVersion`           | No       | `""`                                     | Semantic version range of the supported engine.                                                                                                                              |
| `description`             | No       | `""`                                     | Template description, displayed along with the name in the Modeler.                                                                                                          |
| `keywords`                | No       | `{}`                                     | Search keywords, emitted at the root level of the template JSON.                                                                                                             |
| `propertyGroups`          | No       | `{}`                                     | `@PropertyGroup(id, label, tooltip, openByDefault)` entries defining group labels and order, see [Property groups](#property-groups). `openByDefault` defaults to `true`.   |
| `icon`                    | No       | `""`                                     | Classpath resource path of an SVG or PNG icon, see [Icons](#icons).                                                                                                          |
| `elementTypes`            | No       | `{}`                                     | `@ConnectorElementType` entries, see [BpmnType](#bpmntype).                                                                                                                  |
| `defaultResultVariable`   | No       | `""`                                     | Default value of the result variable property. If empty, no default is set.                                                                                                  |
| `defaultResultExpression` | No       | `""`                                     | Default value of the result expression property. If empty, no default is set.                                                                                                |

## Property types

The Template Generator works out-of-the-box for most data models. If you only use the `@ElementTemplate` annotation
without any additional configuration, it will convert the Connector input data model to an Element Template
using the default rules.

| Java field type                         | Generated template property type |
|-----------------------------------------|----------------------------------|
| `String`                                | `String`                         |
| Number primitives and boxed types       | `Number` (outbound connectors), `String` (inbound connectors) |
| `Boolean`                               | `Boolean`                        |
| Enums                                   | `Dropdown`                       |
| Collections, Maps, `Object`, `JsonNode` | `String` with `feel: required`   |

Everything else gets converted to a `String` by default.

The property type can be customized by using the `@TemplateProperty` annotation:

```java
@TemplateProperty(type = TemplateProperty.PropertyType.Text)
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

## TemplateProperty

`@TemplateProperty` customizes the generated property of a field, method parameter or record component.
All attributes are optional.

| Attribute          | Default                          | Description                                                                                                                                                                                                                  |
|--------------------|----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`               | `""` (field name)                | Custom property ID. Set it explicitly for properties referenced in conditions.                                                                                                                                               |
| `label`            | `""` (derived from field name)   | Custom label.                                                                                                                                                                                                                |
| `binding`          | `@PropertyBinding(name = "")`    | Custom binding name. Defaults to the field name (with the nested path for nested properties).                                                                                                                                |
| `description`      | `""`                             | Property description.                                                                                                                                                                                                        |
| `optional`         | `false`                          | Marks the property as optional.                                                                                                                                                                                              |
| `type`             | `PropertyType.Unknown` (derived) | Overrides the property type, see [Property types](#property-types). Values: `Boolean`, `Number`, `Dropdown`, `Hidden`, `String`, `Text`, `Configuration`, `Unknown`.                                                          |
| `choices`          | `{}`                             | `@DropdownPropertyChoice(value, label)` entries. Only used with `type = Dropdown`, ignored otherwise.                                                                                                                         |
| `feel`             | `FeelMode.system_default`        | FEEL support, see [FeelMode](#feelmode).                                                                                                                                                                                     |
| `defaultValue`     | `""`                             | Default value of the property.                                                                                                                                                                                               |
| `defaultValueType` | `DefaultValueType.String`        | JSON type of the default value: `String`, `Boolean` or `Number`.                                                                                                                                                             |
| `exampleValue`     | `""`                             | Example value of the property. Used by the documentation generator (`ClassBasedDocsGenerator`) as the example for the property instead of generated sample data.                                                                       |
| `group`            | `""` (default group)             | Group ID. Use `@ElementTemplate#propertyGroups` to define labels and order, see [Property groups](#property-groups).                                                                                                         |
| `condition`        | `@PropertyCondition(property = "")` (none) | Condition under which the Modeler renders the property, see [Conditions](#conditions).                                                                                                                             |
| `excludeSubTypes`  | `{}`                             | Sealed subtypes to leave out of the discriminator dropdown for this usage only. Unlike `@TemplateSubType(ignore = true)`, which applies to every template mapping the hierarchy. Properties of excluded subtypes are not emitted either, and a `@TemplateDiscriminatorProperty(defaultValue)` naming an excluded subtype is dropped. Does not recurse into nested sealed hierarchies. |
| `ignore`           | `false`                          | Hides the field from the generator, e.g. for constants or implementation details.                                                                                                                                            |
| `constraints`      | `@PropertyConstraints`           | Validation constraints (`notEmpty`, `minLength`, `maxLength`, `pattern = @Pattern(value, message)`). Explicitly set values override Bean Validation, see [Property validation](#property-validation).                           |
| `tooltip`          | `""`                             | Tooltip text.                                                                                                                                                                                                                |
| `placeholder`      | `""`                             | Placeholder shown while the input is empty. `String` and `Text` properties only.                                                                                                                                             |
| `language`         | `""`                             | Editor language hint. Only `"json"` is supported (enables a JSON visual editor), and only on `String` and `Text` properties. Other values fail generation.                                                                   |
| `secret`           | `false`                          | Marks a secret-bearing field. Only effective within configuration templates, has no effect on host element template properties.                                                                                               |

Notes:
- For `Number` and `Boolean` properties, `feel = FeelMode.disabled` is rejected, and `system_default` results in `staticFeel`.
- For `Dropdown` and `Configuration` properties, `feel` is not applied.
- With `defaultValueType = Number`, the default is parsed into the field's number type for outbound connectors; for inbound connectors it stays a string.

```java
@TemplateProperty(
    id = "method",
    label = "Method",
    group = "endpoint",
    type = TemplateProperty.PropertyType.Dropdown,
    choices = {
      @TemplateProperty.DropdownPropertyChoice(value = "get", label = "GET"),
      @TemplateProperty.DropdownPropertyChoice(value = "post", label = "POST")
    },
    defaultValue = "get",
    description = "HTTP method to use")
private String method;
```

### Conditions

`@PropertyCondition` makes a property visible only if another property matches. Set `property` to the
**ID** of the referenced property and use exactly one of the matchers.

| Attribute      | Description                                                                                                       |
|----------------|-------------------------------------------------------------------------------------------------------------------|
| `property`     | ID of the referenced property. An empty value means "no condition".                                               |
| `equals`       | String equality.                                                                                                  |
| `equalsBoolean` | Boolean equality (`TRUE`, `FALSE`, `NULL`; `NULL` means not set).                                                |
| `oneOf`        | Matches if the value equals one of the given strings.                                                             |
| `allMatch`     | Array of `@NestedPropertyCondition`, all of which must hold (AND). `property` must be left empty when used.      |
| `isActive`     | Condition on whether the referenced property is itself active (its own condition holds). Default `false`.         |
| `isEmpty`      | `NullableBoolean`: `TRUE` = value is empty, `FALSE` = not empty, `NULL` = not used.                               |

`@NestedPropertyCondition` supports the same matchers (`property`, `equals`, `equalsBoolean`, `oneOf`, `isActive`, `isEmpty`) but no further nesting.

```java
@TemplateProperty(
    id = "authType",
    type = TemplateProperty.PropertyType.Dropdown,
    choices = {
      @TemplateProperty.DropdownPropertyChoice(value = "none", label = "None"),
      @TemplateProperty.DropdownPropertyChoice(value = "basic", label = "Basic")
    })
private String authType;

@TemplateProperty(
    label = "Username",
    condition = @TemplateProperty.PropertyCondition(property = "authType", equals = "basic"))
private String username;
```

Setting more than one of `equals`, `equalsBoolean`, `oneOf`, `isEmpty` and `allMatch` fails generation with an `IllegalStateException`.

## DropdownItem

Enums map to `Dropdown` properties. By default, the choice value is the enum constant name and the label is
derived from it. Annotate individual constants with `@DropdownItem` to customize the label and the order.

| Attribute | Required | Default             | Description                                                    |
|-----------|----------|---------------------|----------------------------------------------------------------|
| `label`   | Yes      | —                   | Label shown in the dropdown for the constant.                  |
| `order`   | No       | `Integer.MAX_VALUE` | Position of the choice in the dropdown.                        |

```java
public enum DocumentLocationType {
  @DropdownItem(label = "From Process or URL", order = 0)
  UPLOADED,
  @DropdownItem(label = "From Amazon S3", order = 1)
  S3
}
```

The choice value written to the template stays the enum constant name (`UPLOADED`, `S3`).
Constants without `@DropdownItem` get a derived label and order `0`.

## FeelMode

`FeelMode` is set through `@TemplateProperty#feel` and defines the
[FEEL](https://docs.camunda.io/docs/8.10/components/modeler/feel/what-is-feel/) support of a property.

| Value            | Description                                                                                                                         |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `optional`       | The user can toggle FEEL on and off.                                                                                                |
| `required`       | The value is always a FEEL expression.                                                                                              |
| `staticFeel`     | The field accepts a static value, but is rendered with FEEL-based editing. Default for `Number` and `Boolean` properties.           |
| `disabled`       | No FEEL support. Not valid for `Number` and `Boolean` properties.                                                                   |
| `system_default` | Default. The generator picks the mode from the context: `disabled` for inbound connectors, `optional` for outbound connectors.      |

```java
@TemplateProperty(feel = FeelMode.required)
private Map<String, String> headers;
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
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;

@NestedProperties(condition = @PropertyCondition(property = "someProperty", equals = "someValue"))
private MyNestedInput nested;
```

### NestedProperties

| Attribute       | Default                            | Description                                                                                                                         |
|-----------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `condition`     | `@PropertyCondition(property = "")` (none) | Condition applied to all nested properties. If it does not hold, none of them is visible.                                  |
| `addNestedPath` | `true`                             | Whether the nested path is prefixed to the property name (`foo.bar` vs. `bar`). Disable it to define a custom template structure.   |
| `group`         | `""`                               | Group for all nested properties. Overrides `@TemplateProperty#group` of the nested properties.                                      |

`@NestedProperties` is applicable to fields and record components.

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
an additional discriminator property of type `Dropdown` that gets mapped to a variable in the resulting JSON. Its binding name is derived from the sealed hierarchy root class name, or set with `@TemplateDiscriminatorProperty#name`.

Nested sealed hierarchies are supported as well. The discriminator property is implicitly considered
part of the nested type.

### TemplateDiscriminatorProperty

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

| Attribute      | Required | Default                       | Description                                                                                                                                       |
|----------------|----------|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`         | Yes      | —                             | Binding name of the discriminator property. Also used as the property ID unless `id` is set.                                                      |
| `id`           | No       | `""` (`name`)                 | Property ID. Nested path prefixing applies to it, see [`@NestedProperties#addNestedPath`](#nestedproperties).                                     |
| `label`        | No       | `""` (derived from class name) | Label of the discriminator property.                                                                                                             |
| `group`        | No       | `""` (default group)          | Group of the discriminator property.                                                                                                              |
| `description`  | No       | `""`                          | Description of the discriminator property.                                                                                                        |
| `tooltip`      | No       | `""`                          | Tooltip of the discriminator property.                                                                                                            |
| `defaultValue` | No       | `""`                          | Pre-selected subtype ID.                                                                                                                          |

### TemplateSubType

The sealed variants can be configured by using the `@TemplateSubType` annotation.
It should be placed on the class level of the sealed variant classes.

```java
@TemplateSubType(id = "basic", label = "Basic authentication")
public final class BasicAuthentication extends Authentication {
  @FEEL @NotEmpty private String username;
  @FEEL @NotEmpty private String password;
}
```

| Attribute     | Default                          | Description                                                                                                                                  |
|---------------|----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `id`          | `""` (derived from class name)   | Subtype ID passed in the input payload. Must match the Jackson subtype ID if Jackson handles the polymorphism.                               |
| `label`       | `""` (derived from class name)   | Label of the choice in the discriminator dropdown.                                                                                           |
| `ignore`      | `false`                          | Excludes the subtype from the dropdown in every template that maps its hierarchy. Use `@TemplateProperty#excludeSubTypes` for a single usage. |
| `description` | `""`                             | Description of the subtype, shown in the Modeler search/discovery UI. Only used for hierarchies that participate in operation metadata (step tree); ignored otherwise. |
| `keywords`    | `{}`                             | Search aliases for the subtype. Only required on leaf subtypes of hierarchies that participate in operation metadata (the step tree of operation-based connectors); ordinary sealed hierarchies, e.g. authentication variants, do not need it.                                         |

If you are relying on Jackson to deserialize the polymorphic type, make sure to align the
discriminator property name and subtype IDs with the Jackson configuration.

Note that the [nested properties rules](#nested-properties) also apply to sealed hierarchies.
The discriminator property is considered part of the nested type, so its ID is prefixed with the
nested path like any other nested property (disable it with `@NestedProperties(addNestedPath = false)`).
Make sure the resulting discriminator property IDs are unique within the Connector input data model.

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

`@PropertyGroup` supports `id` (required), `label`, `tooltip` and `openByDefault` (default `true`).

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

Constraints can alternatively be set via `@TemplateProperty#constraints`. The Bean Validation annotations are processed first and
`@TemplateProperty#constraints` second, so explicitly set `minLength`, `maxLength` and `pattern` values of
`@TemplateProperty#constraints` overwrite the ones derived from Bean Validation.

## Document inputs

Properties of type `Document` and `List<Document>` need a richer UI than a plain text field: the user chooses
where the document comes from and supplies the matching data. The document annotations generate this UI.

### TemplateDocumentProperty

`@TemplateDocumentProperty` marks a `Document` or `List<Document>` field, method parameter or record component as
a document input. The generator emits a *Document source* dropdown with source-specific sub-properties, plus a
hidden FEEL composer property that assembles the document JSON read by the runtime.

```java
public record DownloadRequest(
    @TemplateDocumentProperty(
            group = "document",
            tooltip = "The document to be analyzed.",
            sources = {DocumentSource.CAMUNDA, DocumentSource.EXTERNAL},
            fileName = FieldVisibility.HIDDEN,
            contentType = FieldVisibility.HIDDEN)
        Document document) {}
```

| Attribute     | Default                                   | Description                                                                                                                                                                                    |
|---------------|-------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`          | `""`                                      | ID of the hidden composer property (bound to the canonical document path). Set it to keep the ID of a previous equivalent `@TemplateProperty`, so existing templates keep the same root ID.   |
| `binding`     | `@PropertyBinding(name = "")`             | Custom binding name. Defines the binding root of the generated sub-properties.                                                                                                                 |
| `description` | `""`                                      | Property description.                                                                                                                                                                          |
| `optional`    | `false`                                   | Makes the document input optional and changes the generated UI: a single `Document` gets an "Attach document?" Yes/No dropdown (default No), a `List<Document>` gets an additional "None" mode (default). With `false`, the input is mandatory and defaults to the single-document mode.                                                                                                                                                                |
| `group`       | `""`                                      | Group ID. All generated sub-properties inherit it.                                                                                                                                             |
| `condition`   | `@PropertyCondition(property = "")` (none) | Condition prepended to the condition of every generated sub-property. A condition with an empty `property` is ignored, so `allMatch` is not supported here.                                                                                                                         |
| `tooltip`     | `""`                                      | Tooltip text.                                                                                                                                                                                  |
| `sources`     | all [`DocumentSource`](#documentsource) values | Accepted document sources. The first entry is the dropdown default and defines the evaluation order of the FEEL composer. Duplicates are ignored. An empty list is a configuration error.   |
| `fileName`    | `FieldVisibility.OPTIONAL`                | Visibility of the `fileName` sub-property (inline and external sources).                                                                                                                       |
| `contentType` | `FieldVisibility.OPTIONAL`                | Visibility of the `contentType` sub-property of the inline source.                                                                                                                             |

### DocumentSource

| Value      | Dropdown label    | Value in template | Description                                                          |
|------------|-------------------|-------------------|----------------------------------------------------------------------|
| `CAMUNDA`  | Camunda Document  | `camunda`         | Reference to a document already held in Camunda document storage.    |
| `INLINE`   | Inline Content    | `inline`          | Content supplied inline in the model, carried as UTF-8 text bytes.   |
| `EXTERNAL` | From URL          | `external`        | Document fetched at runtime from an external URL.                    |

Narrow `sources` when the connector cannot work with a source, for example the AWS Textract connector omits
`INLINE` because it rejects UTF-8 text bytes.

### FieldVisibility

`FieldVisibility` controls the sub-properties generated by `@TemplateDocumentProperty` (`fileName`, `contentType`) and the encoding sub-property of `@DocumentReturnFormat`.

| Value      | Behavior                                                         |
|------------|------------------------------------------------------------------|
| `REQUIRED` | Shown with a `notEmpty` constraint. For `@DocumentReturnFormat#encoding` it behaves like `OPTIONAL`: no constraint is generated. |
| `OPTIONAL` | Shown without a constraint (default).                            |
| `HIDDEN`   | Not emitted to the template at all; runtime fallbacks apply.     |

### DocumentReturnFormat

`@DocumentReturnFormat` declares that an input class (or a sealed subtype) lets the user choose how a connector
returns a document. The generator emits a `documentReturnFormat` dropdown and an encoding sub-property that
is only shown when `TEXT` is selected.

The bindings are always root-level, regardless of where the annotation is placed: `documentReturnFormat.choice`
and `documentReturnFormat.encoding`. The runtime reads them via
`OutboundConnectorContext#readDocumentReturnFormat()`.

```java
@DocumentReturnFormat(
    group = "input",
    supportedFormats = {DocumentReturnChoice.JSON, DocumentReturnChoice.DOCUMENT},
    defaultFormat = DocumentReturnChoice.JSON,
    encoding = FieldVisibility.HIDDEN)
public record TextractRequest(/* ... */) {}
```

| Attribute          | Default                                 | Description                                                                                                                                                              |
|--------------------|-----------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `label`            | `"Response format"`                     | Dropdown label.                                                                                                                                                          |
| `description`      | `""`                                    | Dropdown description.                                                                                                                                                    |
| `group`            | `""`                                    | Group ID. All generated sub-properties inherit it.                                                                                                                       |
| `condition`        | `@PropertyCondition(property = "")` (none) | Visibility condition. Needed for non-sealed nested records; for sealed subtypes the discriminator condition is added automatically. A condition with an empty `property` is ignored, so `allMatch` is not supported here.                                    |
| `tooltip`          | `""`                                    | Tooltip text.                                                                                                                                                            |
| `supportedFormats` | `{DOCUMENT, TEXT, JSON}`                | `DocumentReturnChoice` values offered in the dropdown, in the given order. Labels: *Document reference*, *as text*, *as JSON*.                                           |
| `defaultFormat`    | `DocumentReturnChoice.DOCUMENT`         | Default selection. Must be one of `supportedFormats`.                                                                                                                    |
| `encoding`         | `FieldVisibility.OPTIONAL`              | Visibility of the encoding sub-property (only shown for `TEXT`). Only `HIDDEN` has an effect; `REQUIRED` and `OPTIONAL` both emit it without a constraint.               |
| `defaultEncoding`  | `"UTF-8"`                               | Default value of the encoding sub-property.                                                                                                                              |

The annotation is `@Inherited`.

## Additional default properties and binding

Default properties added by the generator (`resultVariable`, `errorExpression`, ...) and the binding
rules are described in the [core module](../core/README.md).

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

## Category

By default, the generated element template is assigned to the `connectors`/`Connectors` category.
You can override this by defining a custom category in the `@ElementTemplate` annotation:

```java
@ElementTemplate(
    id = "myConnector",
    name = "My Connector",
    version = 1,
    category = @ElementTemplate.Category(id = "custom-category", name = "Custom Category"))
public class MyConnectorFunction { }
```

Both `id` and `name` must be set when overriding the category. If the category is not specified,
the default `connectors`/`Connectors` category is used.

## BpmnType

`BpmnType` enumerates the BPMN element types a template can target. It is used by
`@ElementTemplate#elementTypes`, which takes `@ElementTemplate.ConnectorElementType` entries:

| Attribute              | Required | Default      | Description                                                                                                                                                       |
|------------------------|----------|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `elementType`          | Yes      | —            | Type the element is transformed into when the template is applied, e.g. `SERVICE_TASK`.                                                                           |
| `appliesTo`            | No       | `{}`         | Types the template can be applied to. If empty, the generator implementation picks the default.                                                                   |
| `templateNameOverride` | No       | `""`         | Overrides the template name for this element type. Default: the class-level name suffixed with the element type, e.g. `My Connector (Service Task)`.              |
| `templateIdOverride`   | No       | `""`         | Overrides the template ID for this element type. Default: the class-level ID suffixed with the element type, e.g. `my-connector:ServiceTask`.                     |

```java
@ElementTemplate(
    id = "my-connector",
    name = "My Connector",
    version = 1,
    elementTypes = {
      @ElementTemplate.ConnectorElementType(
          appliesTo = {BpmnType.TASK},
          elementType = BpmnType.SERVICE_TASK),
      @ElementTemplate.ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT},
          elementType = BpmnType.MESSAGE_END_EVENT,
          templateNameOverride = "My Connector (End event)")
    })
public class MyConnectorFunction { }
```

Values (`getName()` is the BPMN type written to the template, `getId()` is the unique ID used for suffixes;
the `Message type` column shows the enum's own message classification; the generator adds message/correlation properties only for inbound templates targeting `MESSAGE_START_EVENT`, `INTERMEDIATE_CATCH_EVENT`, `BOUNDARY_EVENT` and `RECEIVE_TASK`):

| Value                      | BPMN type                       | Message type |
|----------------------------|---------------------------------|--------------|
| `TASK`                     | `bpmn:Task`                     | No           |
| `SERVICE_TASK`             | `bpmn:ServiceTask`              | No           |
| `RECEIVE_TASK`             | `bpmn:ReceiveTask`              | Yes          |
| `SCRIPT_TASK`              | `bpmn:ScriptTask`               | No           |
| `SEND_TASK`                | `bpmn:SendTask`                 | No           |
| `START_EVENT`              | `bpmn:StartEvent`               | No           |
| `INTERMEDIATE_CATCH_EVENT` | `bpmn:IntermediateCatchEvent`   | Yes          |
| `INTERMEDIATE_THROW_EVENT` | `bpmn:IntermediateThrowEvent`   | Yes          |
| `MESSAGE_START_EVENT`      | `bpmn:StartEvent`               | Yes          |
| `END_EVENT`                | `bpmn:EndEvent`                 | No           |
| `MESSAGE_END_EVENT`        | `bpmn:EndEvent`                 | Yes          |
| `BOUNDARY_EVENT`           | `bpmn:BoundaryEvent`            | Yes          |

## DataExample

`@DataExample` annotates a **static, public, parameterless** method of the Connector's output data class (`@ElementTemplate#outputDataClass`). The generator invokes it without arguments and without changing accessibility, so any other signature fails generation.
The method returns an example result object. The generator serializes it to JSON and shows it as a tooltip on the
result expression property, optionally together with the evaluated FEEL expression.

| Attribute | Default | Description                                                                                                                                                                       |
|-----------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`      | `""`    | ID of the example, can be used when generating documentation. Give each example of a class a distinct, stable ID.                                                                 |
| `feel`    | `""`    | FEEL expression evaluated against the returned example object. The result is shown in the tooltip.                                                                                |

`DataExample.DEFAULT_ID` (`"default"`) marks the canonical example when a class declares more than one.
For the tooltip, the generator uses the example with that ID, or the first example in method-name order if none is marked.
The default of `id` itself stays `""` for backward compatibility.

The expression is evaluated against the example object itself, so write it without the runtime variable prefix
(e.g. `response.`).

```java
public record WebhookOutputExample(MappedHttpRequest request) {

  @DataExample(id = DataExample.DEFAULT_ID, feel = "= { orderId: request.body.orderId }")
  public static WebhookOutputExample example() {
    return new WebhookOutputExample(
        new MappedHttpRequest(Map.of("orderId", "123"), Map.of(), Map.of()));
  }
}
```

If building an example fails (exception in the method or the FEEL evaluation), generation fails.

## TemplateLinkedResource

A [`zeebe:linkedResource`](https://docs.camunda.io/docs/8.10/apis-tools/modeler/element-templates/element-templates-json-schema/#linked-resources)
block declares that a service task depends on a Camunda resource — such as a form —
that Zeebe resolves and attaches at deployment time. Use `@TemplateLinkedResource` on the request
class to generate this block automatically instead of writing the JSON by hand.

> **Note:** `zeebe:linkedResource` is a service-task extension. It is supported on outbound
> connectors (`OutboundConnectorFunction` and `OutboundConnectorProvider`) only. The annotation is
> silently ignored on inbound connectors.

### Basic usage

Annotate the connector's request class (or an `@Operation` parameter type) with
`@TemplateLinkedResource`:

```java
@TemplateLinkedResource(
    linkName = "formDefinition",
    resourceType = "form",
    group = "form",
    resourceIdLabel = "Form ID",
    resourceIdDescription = "ID of the form to attach.",
    bindingTypeLabel = "Form binding")
public record MyRequest(String message) {}
```

The generator produces four properties:

| Property | Type | Description |
|---|---|---|
| Hidden `resourceType` marker | `Hidden` | Carries the `resourceType` value to Zeebe. |
| Binding type | `Dropdown` | Lets the user choose `Latest`, `Deployment`, or `Version tag`. |
| Resource ID | `String` | The ID of the resource to link. FEEL is enabled so secrets and variables can be used. |
| Version tag | `String` | Shown only when the binding type is `Version tag`. FEEL is disabled. |

### Optional linked resources

When the linked resource should be optional — i.e. the process is valid even without it — set
`optional = true`. This prepends a Yes/No toggle (bound as a `zeebe:taskHeader`). All four
linked-resource properties are conditioned on the toggle, so when it is left at the default `No` no
`zeebe:linkedResource` block is written to the BPMN and Zeebe accepts the deployment without a
linked resource.

```java
@TemplateLinkedResource(
    linkName = "formDefinition",
    resourceType = "form",
    group = "form",
    optional = true,
    toggleLabel = "Include form?",
    resourceIdLabel = "Form ID")
public record MyRequest(String message) {}
```

The `toggleLabel` attribute controls the label of the toggle. If left blank it defaults to
`"Include <linkName>?"`.

### Conditional linked resources

Use `conditions` to gate the linked resource on other properties, for example on a discriminator. All
linked-resource properties, including the optional toggle, are additionally conditioned on them. Since a
property whose condition does not hold is not written to the BPMN either, this is an alternative to
`optional` when the resource belongs to one mutually exclusive option, and no Yes/No toggle is needed.

```java
@TemplateLinkedResource(
    linkName = "formDefinition",
    resourceType = "form",
    conditions = @TemplateProperty.NestedPropertyCondition(property = "content.type", equals = "form"))
public record MyRequest(Content content) {}
```

Specify each `property` as declared on the request model, without the operation prefix (the generator
adds it). If the gating discriminator is itself nested inside another discriminator, list **both**,
otherwise the linked resource can still match on a stale value after the outer discriminator changes branch.

### Multiple linked resources

The annotation is repeatable. Each `@TemplateLinkedResource` on the same class must have a unique
`linkName`:

```java
@TemplateLinkedResource(linkName = "preRunScript", resourceType = "RPA", group = "scripts")
@TemplateLinkedResource(linkName = "postRunScript", resourceType = "RPA", group = "scripts")
public record MyRequest(String input) {}
```

The container annotation `@TemplateLinkedResources` is only used by the compiler for repeated
usages and is not meant to be written manually.

### Attribute reference

| Attribute | Required | Default | Description |
|---|---|---|---|
| `linkName` | Yes | — | Symbolic name that groups the generated properties together. Must be unique per class. |
| `resourceType` | Yes | — | Value written to the hidden `resourceType` property (e.g. `"form"`, `"RPA"`). |
| `group` | No | `""` | Property group for the generated fields. |
| `resourceIdLabel` | No | `"Resource ID"` | Label for the resource ID input. |
| `resourceIdDescription` | No | `""` | Description for the resource ID input. Omitted if blank. |
| `bindingTypeLabel` | No | `"Resource binding"` | Label for the binding type dropdown. |
| `optional` | No | `false` | When `true`, adds a Yes/No toggle that gates the linked-resource block. |
| `toggleLabel` | No | `"Include <linkName>?"` | Label for the optional toggle. Only used when `optional = true`. |
| `conditions` | No | `{}` | `@NestedPropertyCondition` entries, all of which must hold. ANDed with the operation scope and the toggle. |
