# Inbound Prototype Example

We use the following example to showcase inbound capabilities:

![ pull request notification example ](./pull-request-notification.png)


## What does it do?

* The process reacts to [Github `pull_request`](https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#pull_request) webhooks.
* Activates on `pull_request.opened` in the `camunda` organization
* Sends a message to the `#general` slack channel notifiying the team on the new PR