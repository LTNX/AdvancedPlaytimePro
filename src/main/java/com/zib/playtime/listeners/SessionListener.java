package com.zib.playtime.listeners;

import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.zib.playtime.Playtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionListener {

    private static final ConcurrentHashMap<UUID, Long> joinTimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> nameCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> historicalCache = new ConcurrentHashMap<>();

    public static void onJoin(PlayerConnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        String name = event.getPlayerRef().getUsername();
        long now = System.currentTimeMillis();

        joinTimes.put(uuid, now);
        nameCache.put(uuid, name);

        new Thread(() -> {
            long dbTime = Playtime.get().getService().getTotalPlaytime(uuid.toString());
            historicalCache.put(uuid, dbTime);
        }).start();
    }

    public static void onQuit(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        String name = event.getPlayerRef().getUsername();
        processSessionSave(uuid, name);
    }

    public static void saveAllSessions() {
        for (Map.Entry<UUID, Long> entry : joinTimes.entrySet()) {
            UUID uuid = entry.getKey();
            String name = nameCache.getOrDefault(uuid, "Unknown");
            processSessionSave(uuid, name);
        }
        joinTimes.clear();
        historicalCache.clear();
        nameCache.clear();
    }

    private static void processSessionSave(UUID uuid, String name) {
        if (joinTimes.containsKey(uuid)) {
            long start = joinTimes.get(uuid);
            long duration = System.currentTimeMillis() - start;

            Playtime.get().getService().saveSession(
                    uuid.toString(),
                    name,
                    start,
                    duration
            );

            joinTimes.remove(uuid);
            historicalCache.remove(uuid);
            nameCache.remove(uuid);
        }
    }

    public static long getCurrentSession(UUID uuid) {
        if (!joinTimes.containsKey(uuid)) return 0;
        return System.currentTimeMillis() - joinTimes.get(uuid);
    }

    public static long getLiveTotalTime(UUID uuid) {
        long history = historicalCache.getOrDefault(uuid, 0L);
        long current = getCurrentSession(uuid);
        return history + current;
    }
}