package com.zib.playtime.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.zib.playtime.Playtime;
import com.zib.playtime.api.PlaytimeAPI;
import com.zib.playtime.config.PlaytimeConfig;
import com.zib.playtime.config.Reward;
import com.zib.playtime.gui.PlaytimeLeaderboardGui;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class PlaytimeCommand extends AbstractPlayerCommand {

    public PlaytimeCommand(String name, String... aliases) {
        super(name, Playtime.get().getConfigManager().getConfig().command.description);
        if (aliases != null && aliases.length > 0) this.addAliases(aliases);
        addUsageVariant(new ActionCommand(name));
        addUsageVariant(new DoubleArgCommand(name));
        addUsageVariant(new TripleArgCommand(name));
        addUsageVariant(new AdminRewardCommand(name));
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef player, World world) {
        if (!ctx.sender().hasPermission("playtime.check")) {
            ctx.sendMessage(color(Playtime.get().getConfigManager().getConfig().messages.noPermission));
            return;
        }
        long total = Playtime.get().getService().getTotalPlaytime(player.getUuid().toString());
        String msg = Playtime.get().getConfigManager().getConfig().messages.selfCheck
                .replace("%time%", format(total))
                .replace("%player%", player.getUsername());
        ctx.sendMessage(color(msg));
    }

    static Message color(String text) {
        if (!text.contains("&")) return Message.raw(text);
        List<Message> messageParts = new ArrayList<>();
        String[] parts = text.split("(?=&[0-9a-fk-or])");
        for (String part : parts) {
            if (part.length() < 2 || part.charAt(0) != '&') {
                messageParts.add(Message.raw(part));
                continue;
            }
            char code = part.charAt(1);
            String content = part.substring(2);
            String hex = getHexFromCode(code);
            if (hex != null) messageParts.add(Message.raw(content).color(hex));
            else messageParts.add(Message.raw(content));
        }
        return Message.join(messageParts.toArray(new Message[0]));
    }

    private static String getHexFromCode(char code) {
        return switch (code) {
            case '0' -> "#000000"; case '1' -> "#0000AA"; case '2' -> "#00AA00";
            case '3' -> "#00AAAA"; case '4' -> "#AA0000"; case '5' -> "#AA00AA";
            case '6' -> "#FFAA00"; case '7' -> "#AAAAAA"; case '8' -> "#555555";
            case '9' -> "#5555FF"; case 'a' -> "#55FF55"; case 'b' -> "#55FFFF";
            case 'c' -> "#FF5555"; case 'd' -> "#FF55FF"; case 'e' -> "#FFFF55";
            case 'f' -> "#FFFFFF"; default -> null;
        };
    }

    static String format(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        return hours + "h " + minutes + "m";
    }

    private static void showTop(CommandContext ctx, PlayerRef player, String periodArg) {
        if (!ctx.sender().hasPermission("playtime.top")) {
            ctx.sendMessage(color(Playtime.get().getConfigManager().getConfig().messages.noPermission));
            return;
        }
        PlaytimeConfig cfg = Playtime.get().getConfigManager().getConfig();
        PlaytimeConfig.PeriodSettings p = cfg.periods;
        String mode = null;
        if (periodArg.equalsIgnoreCase(p.daily)) mode = "daily";
        else if (periodArg.equalsIgnoreCase(p.weekly)) mode = "weekly";
        else if (periodArg.equalsIgnoreCase(p.monthly)) mode = "monthly";
        else if (periodArg.equalsIgnoreCase(p.all)) mode = "all";
        if (mode == null) {
            String valid = String.join(", ", p.daily, p.weekly, p.monthly, p.all);
            ctx.sendMessage(color(cfg.messages.errorInvalidPeriod.replace("%valid_periods%", valid)));
            return;
        }
        Map<String, Long> sorted = Playtime.get().getService().getTopPlayers(mode);
        ctx.sendMessage(color(cfg.messages.leaderboardHeader.replace("%period_name%", periodArg)));
        if (sorted.isEmpty()) { ctx.sendMessage(color(cfg.messages.leaderboardEmpty)); return; }
        int rank = 1;
        for (Map.Entry<String, Long> entry : sorted.entrySet()) {
            String line = cfg.messages.leaderboardEntry
                    .replace("%rank%", String.valueOf(rank))
                    .replace("%player%", entry.getKey())
                    .replace("%time%", format(entry.getValue()));
            ctx.sendMessage(color(line));
            rank++;
        }
    }

    // Comando unificado de 3 args: removeReward, hide, show
    private static class TripleArgCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> arg1;
        private final RequiredArg<String> arg2;
        private final RequiredArg<String> arg3;

        public TripleArgCommand(String name) {
            super(name);
            this.arg1 = withRequiredArg("admin", "Admin", ArgTypes.STRING);
            this.arg2 = withRequiredArg("action", "Action", ArgTypes.STRING);
            this.arg3 = withRequiredArg("target", "Target", ArgTypes.STRING);
        }

        @Override protected boolean canGeneratePermission() { return false; }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef player, World world) {
            if (!ctx.get(arg1).equalsIgnoreCase("admin")) return;
            if (!ctx.sender().hasPermission("playtime.admin")) {
                ctx.sendMessage(color("&cNo tienes permiso para hacer esto."));
                return;
            }
            String action = ctx.get(arg2).toLowerCase();
            String target = ctx.get(arg3);
            switch (action) {
                case "removereward" -> {
                    PlaytimeConfig config = Playtime.get().getConfigManager().getConfig();
                    boolean removed = config.rewards.removeIf(r -> r.id.equals(target));
                    if (removed) {
                        Playtime.get().getConfigManager().save();
                        ctx.sendMessage(color(config.messages.rewardRemoved.replace("%id%", target)));
                    } else {
                        ctx.sendMessage(color(config.messages.rewardNotFound.replace("%id%", target)));
                    }
                }
                case "hide" -> {
                    boolean success = Playtime.get().getService().getDb().hidePlayer(target);
                    if (success) ctx.sendMessage(color("&a" + target + " &7ha sido ocultado del leaderboard."));
                    else ctx.sendMessage(color("&cJugador &f" + target + " &cno encontrado."));
                }
                case "show" -> {
                    boolean success = Playtime.get().getService().getDb().showPlayer(target);
                    if (success) ctx.sendMessage(color("&a" + target + " &7ahora es visible en el leaderboard."));
                    else ctx.sendMessage(color("&cJugador &f" + target + " &cno estaba oculto."));
                }
                default -> ctx.sendMessage(color("&cAccion desconocida. Usa: removeReward, hide, show"));
            }
        }
    }

    private static class ActionCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> arg1;

        public ActionCommand(String name) {
            super(name);
            this.arg1 = withRequiredArg("action", "Action", ArgTypes.STRING);
        }

        @Override protected boolean canGeneratePermission() { return false; }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef player, World world) {
            String arg = ctx.get(arg1);
            PlaytimeConfig config = Playtime.get().getConfigManager().getConfig();
            PlaytimeConfig.PeriodSettings periods = config.periods;

            if (arg.equalsIgnoreCase("help")) { showHelp(ctx); return; }
            if (arg.equalsIgnoreCase("rewards")) { listUserRewards(ctx, player, config); return; }
            if (arg.equalsIgnoreCase("admin")) {
                if (!ctx.sender().hasPermission("playtime.admin")) {
                    ctx.sendMessage(color(config.messages.noPermission)); return;
                }
                showAdminGuide(ctx); return;
            }
            if (arg.equalsIgnoreCase("menu") || arg.equalsIgnoreCase("gui")) {
                if (!ctx.sender().hasPermission("playtime.gui")) {
                    ctx.sendMessage(color(config.messages.noPermission)); return;
                }
                if (ctx.sender() instanceof Player senderPlayer) {
                    senderPlayer.getPageManager().openCustomPage(ref, store, new PlaytimeLeaderboardGui(player));
                }
                return;
            }
            if (arg.equalsIgnoreCase(periods.reload)) {
                if (!ctx.sender().hasPermission("playtime.reload")) {
                    ctx.sendMessage(color(config.messages.reloadNoPermission)); return;
                }
                try {
                    Playtime.get().getConfigManager().load();
                    ctx.sendMessage(color(config.messages.reloadSuccess));
                } catch (Exception e) {
                    ctx.sendMessage(color(config.messages.reloadFailed));
                }
                return;
            }
            if (arg.equalsIgnoreCase("top")) {
                if (config.command.topStyle.equalsIgnoreCase("gui")) {
                    if (ctx.sender() instanceof Player senderPlayer) {
                        senderPlayer.getPageManager().openCustomPage(ref, store, new PlaytimeLeaderboardGui(player));
                    }
                } else {
                    showTop(ctx, player, periods.all);
                }
                return;
            }
            if (arg.equalsIgnoreCase(periods.daily) || arg.equalsIgnoreCase(periods.weekly) ||
                    arg.equalsIgnoreCase(periods.monthly) || arg.equalsIgnoreCase(periods.all)) {
                showTop(ctx, player, arg); return;
            }
            ctx.sendMessage(color("&cUnknown command. Try /playtime help"));
        }

        private void showHelp(CommandContext ctx) {
            ctx.sendMessage(color("&6--- Playtime Help ---"));
            ctx.sendMessage(color("&e/playtime &7- Ver tu tiempo de juego"));
            ctx.sendMessage(color("&e/playtime rewards &7- Ver tus recompensas"));
            ctx.sendMessage(color("&e/playtime top [period] &7- Ver leaderboard"));
            if (ctx.sender().hasPermission("playtime.admin")) {
                ctx.sendMessage(color("&c--- Admin ---"));
                ctx.sendMessage(color("&c/playtime admin &7- Guia de recompensas"));
                ctx.sendMessage(color("&c/playtime admin addReward <id> <period> <time> <cmd>"));
                ctx.sendMessage(color("&c/playtime admin removeReward <id>"));
                ctx.sendMessage(color("&c/playtime admin listRewards"));
                ctx.sendMessage(color("&c/playtime admin hide <jugador>"));
                ctx.sendMessage(color("&c/playtime admin show <jugador>"));
                ctx.sendMessage(color("&c/playtime reload"));
            }
        }

        private void showAdminGuide(CommandContext ctx) {
            ctx.sendMessage(color("&6--- Playtime Admin Guide ---"));
            ctx.sendMessage(color("&e/playtime admin addReward <id> <period> <time> <cmd>"));
            ctx.sendMessage(color("&7- &eid&7: Nombre unico (ej. daily_gold)"));
            ctx.sendMessage(color("&7- &eperiod&7: daily, weekly, monthly, all"));
            ctx.sendMessage(color("&7- &etime&7: 30m, 1h, 1d, 10s"));
            ctx.sendMessage(color("&7- &ecmd&7: Comando. Usa &f%player% &7para el nombre."));
            ctx.sendMessage(color("&e/playtime admin hide <jugador> &7- Ocultar del leaderboard"));
            ctx.sendMessage(color("&e/playtime admin show <jugador> &7- Mostrar en leaderboard"));
        }

        private void listUserRewards(CommandContext ctx, PlayerRef player, PlaytimeConfig cfg) {
            ctx.sendMessage(color(cfg.messages.rewardListHeader));
            String uuid = player.getUuid().toString();
            for (Reward r : cfg.rewards) {
                boolean claimed = Playtime.get().getRewardManager().isClaimed(uuid, r);
                long playtime = PlaytimeAPI.get().getPlaytime(player.getUuid(), r.period);
                boolean eligible = playtime >= r.timeRequirement;
                String status;
                if (claimed) status = cfg.messages.statusClaimed;
                else if (eligible) status = cfg.messages.statusAvailable;
                else status = cfg.messages.statusLocked;
                String line = cfg.messages.rewardListEntry
                        .replace("%id%", r.id)
                        .replace("%period%", r.period)
                        .replace("%status%", status + " &7(" + format(r.timeRequirement) + ")");
                ctx.sendMessage(color(line));
            }
        }
    }

    private static class DoubleArgCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> arg1;
        private final RequiredArg<String> arg2;

        public DoubleArgCommand(String name) {
            super(name);
            this.arg1 = withRequiredArg("arg1", "First Argument", ArgTypes.STRING);
            this.arg2 = withRequiredArg("arg2", "Second Argument", ArgTypes.STRING);
        }
        @Override protected boolean canGeneratePermission() { return false; }
        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef player, World world) {
            String a1 = ctx.get(arg1);
            String a2 = ctx.get(arg2);
            if (a1.equalsIgnoreCase("top")) { showTop(ctx, player, a2.toLowerCase()); return; }
            if (a1.equalsIgnoreCase("admin") && a2.equalsIgnoreCase("listRewards")) {
                if (!ctx.sender().hasPermission("playtime.admin")) {
                    ctx.sendMessage(color("&cNo permission.")); return;
                }
                ctx.sendMessage(color("&6--- Configured Rewards (Admin) ---"));
                PlaytimeConfig cfg = Playtime.get().getConfigManager().getConfig();
                for (Reward r : cfg.rewards) {
                    ctx.sendMessage(color("&eID: &f" + r.id));
                    ctx.sendMessage(color("  &7Period: " + r.period));
                    ctx.sendMessage(color("  &7Time: " + format(r.timeRequirement)));
                    ctx.sendMessage(color("  &7Cmd: " + (r.commands.isEmpty() ? "None" : r.commands.getFirst())));
                }
                return;
            }
            ctx.sendMessage(color("&cUnknown command. Usage: /playtime <action> [arg]"));
        }
    }

    private static class AdminRewardCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> arg1;
        private final RequiredArg<String> arg2;
        private final RequiredArg<String> idArg;
        private final RequiredArg<String> periodArg;
        private final RequiredArg<String> timeArg;
        private final RequiredArg<String> commandArg;

        public AdminRewardCommand(String name) {
            super(name);
            this.arg1 = withRequiredArg("admin", "Admin", ArgTypes.STRING);
            this.arg2 = withRequiredArg("action", "Add", ArgTypes.STRING);
            this.idArg = withRequiredArg("id", "ID", ArgTypes.STRING);
            this.periodArg = withRequiredArg("period", "Period", ArgTypes.STRING);
            this.timeArg = withRequiredArg("time", "Time", ArgTypes.STRING);
            this.commandArg = withRequiredArg("command", "Command", ArgTypes.STRING);
        }

        @Override protected boolean canGeneratePermission() { return false; }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef player, World world) {
            if (!ctx.sender().hasPermission("playtime.admin")) {
                ctx.sendMessage(color("&cYou do not have permission.")); return;
            }
            if (!ctx.get(arg1).equalsIgnoreCase("admin") || !ctx.get(arg2).equalsIgnoreCase("addReward")) return;
            String id = ctx.get(idArg);
            String period = ctx.get(periodArg).toLowerCase();
            String timeStr = ctx.get(timeArg);
            String cmdToRun = ctx.get(commandArg);
            long ms = parseTime(timeStr);
            if (ms <= 0) { ctx.sendMessage(color("&cFormato invalido. Usa 30m, 1h, 1d.")); return; }
            List<String> cmds = new ArrayList<>();
            cmds.add(cmdToRun);
            String broadcast = "&6%player% &eha jugado &6%time% &ey reclamo la recompensa &6" + id + "&e!";
            Reward newReward = new Reward(id, period, ms, cmds, broadcast);
            PlaytimeConfig config = Playtime.get().getConfigManager().getConfig();
            config.rewards.add(newReward);
            Playtime.get().getConfigManager().save();
            ctx.sendMessage(color(config.messages.rewardAdded.replace("%id%", id)));
        }

        private long parseTime(String input) {
            try {
                String number = input.replaceAll("[^0-9]", "");
                String unit = input.replaceAll("[0-9]", "").toLowerCase();
                if (number.isEmpty()) return -1;
                long val = Long.parseLong(number);
                return switch (unit) {
                    case "s" -> val * 1000;
                    case "m" -> val * 60 * 1000;
                    case "h" -> val * 60 * 60 * 1000;
                    case "d" -> val * 24 * 60 * 60 * 1000;
                    default -> val;
                };
            } catch (Exception e) { return -1; }
        }
    }
}