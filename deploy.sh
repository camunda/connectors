#!/bin/bash

PROJECT_ID=${PROJECT_ID:-zeebe-io}
STAGE=${STAGE:-local}

gcloud --project=${PROJECT_ID} functions deploy connector-sendgrid-${STAGE} \
  --region=europe-west1 \
  --entry-point=io.camunda.connector.sendgrid.SendGridFunction \
  --runtime=java11 \
  --trigger-http \
  --source=target/deployment \
  --set-env-vars=SECRETS_PROJECT_ID=${PROJECT_ID},SECRETS_STAGE=${STAGE} \
  --service-account=connector-cloud-functions@zeebe-io.iam.gserviceaccount.com \
  --allow-unauthenticated
