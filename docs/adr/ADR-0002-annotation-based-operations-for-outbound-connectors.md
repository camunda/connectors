# ADR-0002: Annotation-Based Operations for Outbound Connectors

## Status
Accepted

## Context
Outbound connectors traditionally implement `OutboundConnectorFunction`, a single-method interface: `Object execute(OutboundConnectorContext context)`. The function deserializes the entire request with `context.bindVariables(Request.class)` and returns a result.

This works well for a single-purpose connector, but most non-trivial connectors expose several operations (upload, download, search, …). With one entry point, authors must hand-roll dispatch: model the operations as a sealed type hierarchy with Jackson discriminators (`@JsonTypeInfo`/`@JsonSubTypes`), then route with a `switch` or `if-instanceof` chain — sometimes behind a dedicated factory. See `connectors/box` (`BoxFunction` → `BoxOperations.execute`), `connectors/google/google-sheets` (`GoogleSheetsOperationFactory`), and `connectors/automation-anywhere` (`OperationFactory`).

This pattern has recurring costs:
- Dispatch boilerplate grows linearly with the number of operations; each operation needs a subtype, a registration entry, and handler wiring.
- All operations share one method signature, so the inputs an individual operation actually needs are not expressed in any contract — they are dug out of a shared request object.
- Request modeling, routing, and execution are spread across several types, making a connector harder to read than the set of operations it offers.

## Decision
Introduce a method-level programming model built on the `@Operation` annotation. 

A connector implements the marker interface `OutboundConnectorProvider` and exposes one method per operation, each annotated with `@Operation(id = ..., name = ...)`. Method parameters are bound declaratively:

- `@Variable` binds a job variable (resolved from the request JSON);
- `@Header` binds a custom job header;
- an `OutboundConnectorContext` parameter is injected directly, with no annotation.

At runtime, `ConnectorOperations` discovers the annotated methods by reflection and builds a registry keyed by operation id.

`OutboundConnectorOperationFunction` reads the selected operation from the `operation` custom header (`OPERATION_ID_KEYWORD`) and looks up the matching `OperationInvoker`, which binds each parameter — applying required-checks and bean validation per parameter — and invokes the method.

The CSV connector (`connectors/csv/CsvConnector`) is the first connector built this way.

This is intentionally analogous to **Spring MVC**: where a `@Controller` exposes `@RequestMapping` handler methods whose arguments are bound with `@RequestParam`/`@RequestBody`, a connector provider exposes `@Operation` methods whose arguments are bound with `@Variable`/`@Header`. Operations become routed endpoints with precise signatures, rather than branches inside one monolithic function.

The `@Operation` model is the **recommended default for new connectors and any connector with multiple operations**. The classic `OutboundConnectorFunction` interface **remains fully supported** and is the natural choice for existing connectors and genuinely single-purpose ones; it is not deprecated.

## Consequences

### Positive
- Eliminates hand-written dispatch — the runtime routes on operation id, so no `switch`/factory grows with each operation.
- Each operation is a self-documenting method whose signature states exactly the variables and headers it consumes.
- Declarative binding removes manual `bindVariables` calls and casting from connector code.
- Adding an operation is a single annotated method, lowering the cost and review surface of extending a connector.
- The model mirrors Spring controllers, so developers familiar with that style are immediately productive.
- Required-parameter checks and bean validation are applied uniformly by `OperationInvoker` instead of being re-implemented per connector.

### Negative
- Reflection-based dispatch is less explicit than a `switch`: call paths are harder to trace statically, and some errors (missing or duplicate operation id, unsupported parameter type) surface at runtime rather than compile time.
- Two supported models increase the conceptual surface area and the documentation and example burden.
- The fixed set of parameter sources (`@Variable`, `@Header`, `OutboundConnectorContext`) constrains advanced cases that the classic single-request style can express directly.
- Reflective method invocation adds minor overhead and offers weaker compile-time guarantees than a directly implemented interface method.
