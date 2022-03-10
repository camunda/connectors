#!/bin/bash

PROJECT_ID=${PROJECT_ID:-zeebe-io}
STAGE=${STAGE:-local}

gcloud --project=${PROJECT_ID} functions deploy connector-sendgrid-${STAGE} \
  --region=europe-west1 \
  --update-labels=stage=${STAGE} \
  --entry-point=io.camunda.connector.sendgrid.SendGridFunction \
  --runtime=java11 \
  --trigger-http \
  --source=target/deployment \
  --set-env-vars=SECRETS_PROJECT_ID=${PROJECT_ID} \
  --service-account=cloud-connectors@zeebe-io.iam.gserviceaccount.com \
  --allow-unauthenticated
