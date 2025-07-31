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
    # Remove tool-specific and error handling properties
    map(select(.id != "data.tools.containerElementId" and
               .id != "data.tools.toolCallResults" and
               .id != "errorExpression" and
               .id != "retryCount" and
               .id != "retryBackoff")) |

    # Update specific property values and bindings
    map(
      if .binding.property == "type" then
        .value = "io.camunda.agenticai:aiagent-job-worker:1"
      elif .id == "resultVariable" then
        .binding = {source: "=agent", type: "zeebe:output"} |
        .value = "agent"
      elif .id == "resultExpression" then
        # Reconstruct the entire resultExpression object with correct property order
        {
          id: .id,
          label: .label,
          description: "Expression to define how to map the Agent response into the result variable",
          feel: "required",
          value: "={\n  responseMessage: response.responseMessage,\n  responseText: response.responseText,\n  responseJson: response.responseJson\n}",
          group: .group,
          binding: .binding,
          type: .type
        }
      else . end
    )
  )
' "$SOURCE_FILE"
