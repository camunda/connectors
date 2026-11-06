# System prompt contributor SPI

## SystemPromptContributor Interface

```java
public interface SystemPromptContributor {
    String contribute(AgentExecutionContext executionContext, AgentContext agentContext);
    default int getOrder() { return 0; }  // lower values sort earlier
}
```

Spring auto-wires all `SystemPromptContributor` beans into the composer.
