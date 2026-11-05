# OpenAI Connector

The **OpenAI Connector** allows you to use [ChatGPT](https://platform.openai.com/docs/guides/chat/chat-completions-beta)
or [Moderation API](https://platform.openai.com/docs/guides/moderation/moderation) in your Camunda 8 processes.

This Connector reuses the base implementation of [HTTP JSON Connector](../http-json) by providing a compatible element template.

## What can I use it for?

Large language models like GPT-3 or GPT-4 can create and edit content, or respond to user prompt in an interactive manner.
Please refer to the [example API use-cases](https://platform.openai.com/examples) provided by OpenAI.

![process example](../openai/img/process_example.png)

## Model selection

Enter a model ID in the **Model** field, either as text or a FEEL expression.
The placeholder is an example, not a default: you must explicitly select a model.
Refer to the [Models](https://platform.openai.com/docs/models) section of OpenAI documentation
for current models. Availability depends on your account privileges.

Template version 9 replaces the model dropdown. Existing named selections remain valid.
Before upgrading a template that uses **Custom**, save **Custom model version**, then paste it
into **Model** after the upgrade. The request expression also supports legacy `custom` selections when
the `internal_custom_model` input is still present. Older template versions remain available.

The Google Gemini and Bedrock embeddings templates follow the same free-text pattern starting
with version 5. Their connectors still accept legacy dropdown values. When upgrading a **Custom**
selection, save **Custom model name** and paste it into the new model field after the upgrade.
Bedrock embeddings require a model compatible with the Amazon Titan embedding API;
dimensions and normalization are available for `amazon.titan-embed-text-v2:0`.

## Moderation

It is recommended to use the Moderation API to sanitize both inputs and outputs of the language model.
This way you will be able to prevent violation of OpenAI policies and displaying the potentially unsafe content in your system.
