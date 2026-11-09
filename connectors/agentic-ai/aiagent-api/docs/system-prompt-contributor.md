# System prompt contributor SPI

## SystemPromptContributor Interface

`SystemPromptContributor#contribute` returns the text to append to the system prompt for the current
`AgentExecutionContext` and `AgentContext`, or `null` or an empty string to add nothing.
`SystemPromptContributor#getOrder` sets the position of the contribution: lower values are appended
first, and the default is `0`.

Spring auto-wires all `SystemPromptContributor` beans into the composer.
