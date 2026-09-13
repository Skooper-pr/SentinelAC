package com.skooper.sentinelac.reporting;

import com.skooper.sentinelac.fusion.model.FusionFlagEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dispatches asynchronous webhook alerts to Discord when cheating flags occur.
 * Completely free, pure Java HttpClient, config-gated and disabled by default.
 */
public final class DiscordWebhookNotifier {

    private final Logger logger;
    private final HttpClient httpClient;
    private volatile boolean enabled = false;
    private volatile String webhookUrl = "";

    public DiscordWebhookNotifier(Logger logger) {
        this.logger = logger != null ? logger : Logger.getLogger(DiscordWebhookNotifier.class.getName());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void configure(boolean enabled, String webhookUrl) {
        this.enabled = enabled;
        this.webhookUrl = webhookUrl != null ? webhookUrl.trim() : "";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    /**
     * Sends an asynchronous Discord webhook alert for a detected flag.
     *
     * @param playerName Player display name.
     * @param event      The fusion flag event.
     * @param flagId     The database ID of the flag.
     */
    public CompletableFuture<Void> sendAlertAsync(String playerName, FusionFlagEvent event, int flagId) {
        if (!enabled || webhookUrl.isEmpty() || !webhookUrl.startsWith("http")) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                String payload = buildJsonPayload(playerName, event, flagId);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .header("User-Agent", "SentinelAC-Notifier/1.0")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    logger.warning("Discord webhook returned non-2xx status: " + response.statusCode());
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send Discord webhook alert: " + e.getMessage());
            }
        });
    }

    private String buildJsonPayload(String playerName, FusionFlagEvent event, int flagId) {
        String escapedPlayer = escapeJson(playerName);
        String escapedSummary = escapeJson(event.getSummary());
        String category = event.getCategory().name();
        String type = event.isDeterministic() ? "Deterministic Proof (Violation)" : "SPRT Confidence Boundary Crossed";
        String flagIdStr = flagId > 0 ? String.valueOf(flagId) : "Pending";

        return String.format("""
            {
              "username": "SentinelAC",
              "embeds": [
                {
                  "title": "🚨 SentinelAC Detection Alert",
                  "color": 15158332,
                  "fields": [
                    { "name": "Player", "value": "`%s` (%s)", "inline": true },
                    { "name": "Category", "value": "`%s`", "inline": true },
                    { "name": "Detection Type", "value": "%s", "inline": false },
                    { "name": "Lambda (Λ)", "value": "%.3f (threshold: %.3f)", "inline": true },
                    { "name": "Flag ID", "value": "`#%s`", "inline": true },
                    { "name": "Evidence Summary", "value": "```%s```", "inline": false }
                  ],
                  "footer": { "text": "Use /sentinelac verdict %s <upheld|overturned> to record staff outcome." }
                }
              ]
            }
            """,
                escapedPlayer, event.getPlayerId().toString(),
                category, type,
                event.getLambdaAtFlag(), event.getThreshold(),
                flagIdStr, escapedSummary, flagIdStr
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
