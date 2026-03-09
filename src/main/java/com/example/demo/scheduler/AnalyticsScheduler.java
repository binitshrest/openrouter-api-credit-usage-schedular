package com.example.demo.scheduler;

import com.example.demo.service.OpenRouterAnalyticsService;
import com.example.demo.service.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsScheduler {

    private final OpenRouterAnalyticsService analyticsService;
    private final TelegramBotService telegramBotService;

    private final DecimalFormat df = new DecimalFormat("#.##", new java.text.DecimalFormatSymbols(java.util.Locale.US));
    private final DecimalFormat modelDf = new DecimalFormat("#.####",
            new java.text.DecimalFormatSymbols(java.util.Locale.US));

    // Run every day at 10 PM Kathmandu time
    @Scheduled(cron = "${bot.cron.schedule:0 0 22 * * ?}", zone = "Asia/Kathmandu")
    public void runDailyReport() {
        generateAndSendReport();
    }

    // Poll for Telegram commands every 100ms
    @Scheduled(fixedDelay = 100)
    public void pollForCommands() {
        if (telegramBotService.hasCommand("/totusg")) {
            log.info("Received /totusg command. Triggering report...");
            generateAndSendReport();
        }
    }

    public void runImmediateForTesting() {
        generateAndSendReport();
    }

    public void generateAndSendReport() {
        log.info("Starting OpenRouter Analytics report generation...");
        OpenRouterAnalyticsService.AnalyticsData data = analyticsService.fetchAnalytics();
        String report = buildReportMessage(data);
        telegramBotService.sendMessage(report);
    }

    private String buildReportMessage(OpenRouterAnalyticsService.AnalyticsData data) {
        StringBuilder sb = new StringBuilder();

        int histRequests = data.getModels().stream().mapToInt(OpenRouterAnalyticsService.ModelStat::getRequests).sum();
        long histInTokens = data.getModels().stream().mapToLong(OpenRouterAnalyticsService.ModelStat::getInTokens)
                .sum();
        long histOutTokens = data.getModels().stream().mapToLong(OpenRouterAnalyticsService.ModelStat::getOutTokens)
                .sum();
        long totalTokens = histInTokens + histOutTokens;

        // Find model with max tokens
        OpenRouterAnalyticsService.ModelStat maxTokenModel = data.getModels().stream()
                .max(java.util.Comparator.comparingLong(m -> m.getInTokens() + m.getOutTokens()))
                .orElse(null);

        sb.append("🚀 <b>OPENROUTER ANALYTICS PREMIUM</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // --- Account section ---
        sb.append("💰 <b>ACCOUNT STATUS</b>\n");
        sb.append("<b>Balance:</b> ").append(generateCreditBar(data.getTotalSpent(), data.getTotalCredits()))
                .append("\n");
        sb.append("<b>Total Consumed:</b> $").append(modelDf.format(data.getTotalSpent()))
                .append(" / $").append(df.format(data.getTotalCredits()))
                .append(" (").append(calculatePercentage(data.getTotalSpent(), data.getTotalCredits()))
                .append("%)\n");
        sb.append("<b>Today (Real-Time):</b> $").append(modelDf.format(data.getTodaySpent())).append("\n");
        sb.append("<b>Remaining:</b> $").append(modelDf.format(data.getTotalCredits() - data.getTotalSpent()))
                .append("\n\n");

        // --- Usage section ---
        sb.append("📊 <b>USAGE SUMMARY</b>\n");
        sb.append("• <b>Total Spent:</b> $").append(modelDf.format(data.getTotalSpent())).append(" (Actual)\n");
        sb.append("• <b>Historical Tokens:</b> ").append(formatTokens(totalTokens)).append(" (Synced)\n");
        if (maxTokenModel != null) {
            sb.append("• <b>Token Champion:</b> <code>").append(maxTokenModel.getModelName()).append("</code>\n");
        }
        sb.append("• <b>Completed Requests:</b> ").append(java.text.NumberFormat.getInstance().format(histRequests))
                .append("\n\n");

        // --- Model Breakdown ---
        if (!data.getModels().isEmpty() || data.getUnclassifiedUsage() > 0.01) {
            sb.append("🧱 <b>MODEL BREAKDOWN (Historical)</b>\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");

            // 1. Show Current/Pending Activity first
            if (data.getUnclassifiedUsage() >= 0.01) {
                sb.append("<b>⏳ TODAY'S REAL-TIME DELTA</b>\n");
                sb.append("   💵 ").append(generateMiniBar(data.getUnclassifiedUsage(), data.getTotalCredits()))
                        .append(" $").append(modelDf.format(data.getUnclassifiedUsage())).append("\n");
                sb.append("   <i>(Breakdown & tokens pending sync)</i>\n\n");
            }

            // Sort models by cost descending
            data.getModels().sort((a, b) -> Double.compare(b.getCost(), a.getCost()));

            int index = 1;
            for (OpenRouterAnalyticsService.ModelStat model : data.getModels()) {
                if (model.getCost() < 0.01 && model.getRequests() <= 0)
                    continue;

                sb.append("<b>").append(index++).append(". ").append(model.getModelName()).append("</b>\n");
                // Model Credit Usage
                sb.append("   💵 ").append(generateMiniBar(model.getCost(), data.getTotalCredits()))
                        .append(" $").append(modelDf.format(model.getCost())).append("\n");
                sb.append("   🪙 in ").append(formatTokens(model.getInTokens())).append(" / out ")
                        .append(formatTokens(model.getOutTokens())).append("\n");
                sb.append("   🔢 ").append(java.text.NumberFormat.getInstance().format(model.getRequests()))
                        .append(" calls\n\n");
            }
        }

        // --- Footer ---
        java.time.ZonedDateTime nowKathmandu = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kathmandu"));
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd hh:mm a");

        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("Data current as of: ").append(nowKathmandu.format(formatter)).append(" (NPT)\n");
        sb.append("<i>Token counts update after UTC day completion.</i>");

        return sb.toString();
    }

    private String calculatePercentage(double spent, double total) {
        if (total <= 0)
            return "0";
        return df.format((spent / total) * 100);
    }

    private String generateCreditBar(double spent, double total) {
        if (total <= 0)
            total = 10.0;
        int barLength = 20;
        int burntLength = (int) Math.round((spent / total) * barLength);
        if (spent > 0 && burntLength == 0)
            burntLength = 1;
        if (burntLength > barLength)
            burntLength = barLength;
        int remainingLength = barLength - burntLength;

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < burntLength; i++)
            bar.append("🟥");
        for (int i = 0; i < remainingLength; i++)
            bar.append("🟩");
        bar.append("]");
        return bar.toString();
    }

    private String generateMiniBar(double cost, double totalCredits) {
        if (totalCredits <= 0)
            totalCredits = 10.0;
        int barLength = 10;

        // Proportional to the total account limit
        int spentLength = (int) Math.round((cost / totalCredits) * barLength);
        if (cost > 0 && spentLength == 0)
            spentLength = 1;
        if (spentLength > barLength)
            spentLength = barLength;

        int remainingLength = barLength - spentLength;

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < spentLength; i++)
            bar.append("🟥");
        for (int i = 0; i < remainingLength; i++)
            bar.append("🟩");
        bar.append("]");
        return bar.toString();
    }

    private String formatTokens(long amount) {
        if (amount >= 1_000_000) {
            return df.format(amount / 1_000_000.0) + "M";
        } else if (amount >= 1_000) {
            return df.format(amount / 1_000.0) + "K";
        }
        return String.valueOf(amount);
    }
}
