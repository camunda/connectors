#!/bin/bash

set -eu

SOURCE_FILE=$1

if [ ! -f "$SOURCE_FILE" ]; then
    echo "Error: Source file $SOURCE_FILE not found" >&2
    exit 1
fi

jq -r '
  # Update template metadata
  .id = "io.camunda.connectors.agenticai.aiagent.jobworker.v1" |

  # Change BPMN element configuration
  .appliesTo = ["bpmn:SubProcess"] |
  .elementType.value = "bpmn:AdHocSubProcess" |

  # Remove error and retries groups
  .groups = (.groups | map(select(.id != "error" and .id != "retries"))) |

  # Transform properties
  .properties = (.properties |
    # Remove tool-specific, error handling properties, and resultExpression
    map(select(.id != "data.tools.containerElementId" and
               .id != "data.tools.toolCallResults" and
               .id != "errorExpression" and
               .id != "retryCount" and
               .id != "retryBackoff" and
               .id != "resultExpression")) |

    # Update specific property values and bindings
    map(
      if .binding.property == "type" then
        .value = "io.camunda.agenticai:aiagent-job-worker:1"
      elif .id == "resultVariable" then
        .binding = {source: "=agent", type: "zeebe:output"} |
        .value = "agent"
      else . end
    ) |

    # Add the new includeAgentContext property after includeAssistantMessage
    map(
      if .id == "data.response.includeAssistantMessage" then
        ., {
          "id": "data.response.includeAgentContext",
          "label": "Include agent context",
          "description": "Include the agent context as part of the result object.",
          "optional": true,
          "feel": "static",
          "group": "response",
          "binding": {
            "name": "data.response.includeAgentContext",
            "type": "zeebe:input"
          },
          "tooltip": "Use this option if you need to re-inject the previous agent context into a future agent execution, for example when modeling a user feedback loop between an agent and a user task.",
          "type": "Boolean"
        }
      else . end
    ) | flatten
  )
' "$SOURCE_FILE"
