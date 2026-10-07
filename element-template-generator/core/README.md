# Element Template Generator

This is a tool to generate [element templates](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-templates/)
based on the Connector Java code.

> **Annotations:** the annotations that configure the generator (`@ElementTemplate`, `@TemplateProperty`,
> `@TemplateDocumentProperty`, ...) are defined in the
> [`element-template-generator-annotations`](../annotations) module. Their reference, including
> attributes, defaults and examples, is in the [annotations README](../annotations/README.md).

## Prerequisites

To make use of the Template Generator, your Connector must:
- Define data models for Connector inputs by means of a Java class that is used to deserialize the input JSON;
- Generally rely on the `bindVariables` method of the `OutboundConnectorContext` (and not the low-level `getVariables` method);
- Be annotated with the `@ElementTemplate` annotation (see the [annotations README](../annotations/README.md#elementtemplate)).

## Usage

For most use cases, we recommend using the [Maven plugin](../maven-plugin) to invoke the Template Generator.

The Generator can be invoked directly from Java code as well. To do so, create an instance of the
`ClassBasedElementTemplateGenerator` class and invoke its `generate` method.

```java
OutboundElementTemplateGenerator generator = new OutboundElementTemplateGenerator();
OutboundElementTemplate template = generator.generate(MyConnectorFunction.class);
```

The resulting object can be serialized using Jackson (pre-configured) or any other JSON library.

## Default type mapping

Without additional annotations, the generator converts the Connector input data model using the default
rules. See [Property types](../annotations/README.md#property-types) for the mapping table and for
customization via `@TemplateProperty`.

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

## Element Template DSL

This module defines a DSL for building element templates programmatically. The starting point is the
`ElementTemplate` class. You can use the DSL directly to build the template and then invoke
the `build` method. The resulting `ElementTemplate` object can be serialized to JSON using Jackson
(pre-configured) or any other JSON library (would require custom configuration).
