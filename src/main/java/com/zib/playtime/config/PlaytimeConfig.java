package com.zib.playtime.config;

import java.util.Arrays;
import java.util.List;

public class PlaytimeConfig {

    public DatabaseSettings database = new DatabaseSettings();
    public CommandSettings command = new CommandSettings();
    public PeriodSettings periods = new PeriodSettings();
    public MessageSettings messages = new MessageSettings();
    public GuiSettings gui = new GuiSettings();

    public static class DatabaseSettings {
        public String type = "sqlite";
        public String host = "localhost";
        public int port = 3306;
        public String databaseName = "playtime_db";
        public String username = "root";
        public String password = "password";
        public boolean useSSL = false;
    }

    public static class CommandSettings {
        public String name = "playtime";
        public String description = "Check your playtime stats";
        public List<String> aliases = Arrays.asList("pt", "play", "time");
    }

    public static class PeriodSettings {
        public String daily = "daily";
        public String weekly = "weekly";
        public String monthly = "monthly";
        public String all = "all";
        public String reload = "reload";
    }

    public static class MessageSettings {
        public String selfCheck = "&dTotal Playtime: &e%time%";
        public String otherCheck = "&d%player%'s Playtime: &e%time%";

        public String leaderboardHeader = "&6--- Playtime Leaderboard (&e%period_name%&6) ---";
        public String leaderboardEntry = "&6#%rank% &e%player% &7: &f%time%";
        public String leaderboardEmpty = "&7No data available yet.";

        public String reloadSuccess = "&aConfiguration reloaded successfully!";
        public String reloadNoPermission = "&cYou do not have permission to reload.";
        public String reloadFailed = "&cFailed to reload config. Check console.";

        public String errorInvalidPeriod = "&cInvalid period. Use: %valid_periods%";
        public String errorConsole = "&cPlayers only.";
    }

    public static class GuiSettings {
        public String title = "LEADERBOARD";
        public String buttonAll = "ALL TIME";
        public String buttonDaily = "DAILY";
        public String buttonWeekly = "WEEKLY";
        public String buttonMonthly = "MONTHLY";
        public String footerTitle = "YOUR STATS:";
        public String rankPrefix = "Rank: #";
        public String timePrefix = "Time: ";
    }
}