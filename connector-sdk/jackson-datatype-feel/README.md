# Jackson Datatype FEEL

This module provides a Jackson datatype for FEEL.
It allows to parse and evaluate FEEL expressions contained in JSON data during deserialization.

## Features

This module enables the following deserializers:
* `FeelDeserializer` - a multipurpose deserializer that can be used to evaluate FEEL expressions during deserialization.
* `FeelFunctionDeserializer` - a deserializer that can be used to parse FEEL expressions into Java `Function` objects.
* `FeelSupplierDeserializer` - a deserializer that can be used to parse FEEL expressions into Java `Supplier` objects.

## Usage

### Maven

Add the following dependency to your project:

```xml
<dependency>
  <groupId>io.camunda.connector</groupId>
  <artifactId>jackson-datatype-feel</artifactId>
  <version>${version.camunda-connector}</version>
</dependency>
```

### Deserializing FEEL expressions

The `FeelDeserializer` can be used to evaluate FEEL expressions during deserialization.
To enable it, register the `FeelModule` with your `ObjectMapper`:

```java
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.registerModule(new JacksonModuleFeelFunction());
```

To enable the FEEL deserializer for a specific property, you can either use the standard Jackson annotations:

```java
@JsonDeserialize(using = FeelDeserializer.class)
public String feelExpression;
```

or the `@FEEL` annotation provided by the `connector-core` module (optional dependency):

```java
@FEEL
public String feelExpression;
```

### FEEL function deserialization

The `FeelFunctionDeserializer` can be used to parse FEEL expressions into Java `Function` objects.
It is enabled when you register the `JacksonModuleFeelFunction` with your `ObjectMapper`.

```java
record MyDataClass(Function<Object, String> converter) {}
```

The example above will deserialize the following JSON data:

```json
{
  "converter": "= a + b"
}
```

You will be able to use the `converter` property as a `Function` and provide a context that contains the variables `a` and `b`:

```java
MyDataClass myDataClass = objectMapper.readValue(json, MyDataClass.class);
String result = myDataClass.converter.apply(
    Map.of("a", "Hello", "b", " World!"")); // "Hello World!"
```

### FEEL supplier deserialization

The `FeelSupplierDeserializer` can be used to parse FEEL expressions into Java `Supplier` objects.
It is enabled when you register the `JacksonModuleFeelFunction` with your `ObjectMapper`.

```java
record MyDataClass(Supplier<String> supplier) {}
```

The example above will deserialize the following JSON data:

```json
{
  "supplier": "= \"Hello\" + \" World!\"}"
}
```

You will be able to use the `supplier` property as a `Supplier`:

```java
MyDataClass myDataClass = objectMapper.readValue(json, MyDataClass.class);
String result = myDataClass.supplier.get(); // "Hello World!"
```

## Dynamic FEEL context

You can provide dynamically resolved context to any of the deserializers by using the Jackson `DeserializationContext`.
Use the `FeelContextAwareObjectReader` API to provide the context supplier:

```java
ObjectReader objectReader = FeelContextAwareObjectReader.create(objectMapper).withContextSupplier(() -> Map.of("a", "Hello", "b", " World!"));
MyDataClass myDataClass = objectReader.readValue(json, MyDataClass.class);
```

Now any FEEL expression will be evaluated with the provided context.
