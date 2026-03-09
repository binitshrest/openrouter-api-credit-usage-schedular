# OpenRouter Analytics Bot

A Spring Boot application that automatically fetches your OpenRouter API usage statistics, model breakdowns, and credit balances, and sends a daily summary report to a specified Telegram chat.

## Features

- **Daily Reporting:** Scheduled to run daily and send a comprehensive breakdown of API usage.
- **OpenRouter Integration:** Fetches token usage, credit consumption, and cost data directly from the OpenRouter API.
- **Telegram Notifications:** Delivers neatly formatted reports to your Telegram chat, including a visualization of credit consumption.
- **Dockerized:** Easy deployment using Docker and Docker Compose.
- **Secure Configuration:** Uses `.env` files combined with Docker volumes to securely manage sensitive API keys and tokens.

## Prerequisites

- Java 17 or higher (for local development)
- Docker and Docker Compose (for containerized deployment)
- An OpenRouter account with a [Management Key](https://openrouter.ai/keys)
- A Telegram Bot Token (from [@BotFather](https://t.me/botfather)) and your Telegram Chat ID

## Setup and Installation

### 1. Clone the repository

```bash
git clone https://github.com/binitshrest/openrouter-api-credit-usage-schedular.git
cd openrouter-analytics-bot
```

### 2. Configuration

Create a `.env` file in the root directory of the project. This file is ignored by Git to keep your secrets safe. Add your credentials:

```properties
# .env
OPENROUTER_MANAGEMENT_KEY=your_openrouter_management_key
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
TELEGRAM_CHAT_ID=your_telegram_chat_id
```

### 3. Running Locally (Without Docker)

You can run the application directly using the Gradle wrapper. Ensure you have your environment variables set or load them using a plugin if running from an IDE.

```bash
# On Linux/macOS
./gradlew bootRun

# On Windows
gradlew.bat bootRun
```

### 4. Running with Docker Compose (Recommended)

The easiest way to run the bot reliably in the background is using Docker Compose. It will build the image and mount your `.env` file securely without exposing it as OS-level environment variables inside the container.

```bash
docker-compose up -d --build
```

To view the logs and verify it started successfully:

```bash
docker-compose logs -f
```

## How It Works

- The application uses Spring Boot's `@Scheduled` annotation to trigger the report generation.
- By default, the cron cron schedule is set to `0 0 22 * * ?` (10:00 PM system time every day).
- When triggered, it fetches your credit limits, calculates your total active usage, and fetches a breakdown of the models you've used today.
- It then formats a detailed Markdown message and dispatches it via the Telegram Bot API.

## Customization

You can modify the cron schedule and logging levels in `src/main/resources/application.properties`:

```properties
bot.cron.schedule=0 0 22 * * ?
logging.level.com.example.demo=DEBUG
```

## Sample Output

Here are examples of the messages you will receive in Telegram detailing your API credit usage:

### Account status / summary

![OpenRouter Analytics - Account status](https://github.com/user-attachments/assets/afb21a96-ec22-4341-a6f4-f0b398577b26)

### Model breakdown

![OpenRouter Analytics - Model breakdown](https://github.com/user-attachments/assets/5c1f83d5-1060-4b24-ac0d-a6a07a6e293f)

## Manual Triggering

In addition to the daily scheduled reports, you can trigger a report manually at any time by sending the following command to your bot in Telegram:

```
/totusg
```

For API testing purposes, you can also trigger a report manually if you have set up a workflow or testing endpoint. (See `.agent/workflows/send-report.md` if using the specific agent extension).

## Contributors

- **Binit Shrestha**
- **Google Antigravity**