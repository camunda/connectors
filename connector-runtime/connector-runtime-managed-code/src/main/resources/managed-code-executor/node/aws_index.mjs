// AWS Lambda adapter of the generic Node.js executor.
import { run } from './executor_core.mjs';

const SAFETY_MS = 1_000;

export const handler = async (event, context) => run(event, context.getRemainingTimeInMillis() - SAFETY_MS);
