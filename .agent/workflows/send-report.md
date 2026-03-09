---
description: Trigger a manual OpenRouter Analytics report
---
1. Run the manual trigger test to send a report to Telegram.
// turbo
2. run_command: `export $(grep -v '^#' .env | xargs) && ./gradlew cleanTest test --tests com.example.demo.ManualTriggerTest --info`
