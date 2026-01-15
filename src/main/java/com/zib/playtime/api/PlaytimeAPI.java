package com.zib.playtime.api;

import com.zib.playtime.Playtime;
import com.zib.playtime.PlaytimeService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlaytimeAPI {

    private static PlaytimeAPI instance;
    private final PlaytimeService service;

    private PlaytimeAPI() {
        this.service = Playtime.get().getService();
    }

    /**
     * Get the singleton instance of the API.
     * @return PlaytimeAPI instance.
     */
    public static PlaytimeAPI get() {
        if (instance == null) {
            instance = new PlaytimeAPI();
        }
        return instance;
    }

    /**
     * Get the total playtime of a player in milliseconds.
     * This includes the current live session if the player is online.
     * * @param uuid The UUID of the player.
     * @return Total playtime in ms.
     */
    public long getTotalPlaytime(UUID uuid) {
        return service.getTotalPlaytime(uuid.toString());
    }

    /**
     * Get playtime for a specific period.
     * This includes the current live session if the player is online.
     * * @param uuid The UUID of the player.
     * @param period The period: "daily", "weekly", "monthly", "all".
     * @return Playtime in ms.
     */
    public long getPlaytime(UUID uuid, String period) {
        return service.getPlaytime(uuid.toString(), period);
    }

    /**
     * Get the rank of a player for a specific period.
     * * @param uuid The UUID of the player.
     * @param period The period: "daily", "weekly", "monthly", "all".
     * @return The rank (1-based), or 0 if not ranked.
     */
    public int getRank(UUID uuid, String period) {
        return service.getRank(uuid.toString(), period);
    }

    /**
     * Get the top 10 players for a specific period.
     * * @param period The period: "daily", "weekly", "monthly", "all".
     * @return A map of Username -> Playtime (ms).
     */
    public Map<String, Long> getTopPlayers(String period) {
        return service.getTopPlayers(period);
    }

    /**
     * Utility: Format milliseconds into a readable string (e.g. "2h 5m").
     * Useful for consistency across plugins.
     * * @param millis Time in milliseconds.
     * @return Formatted string.
     */
    public String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        return hours + "h " + minutes + "m";
    }
}