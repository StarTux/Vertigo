package io.github.feydk.vertigo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class State {
    protected List<String> worlds = new ArrayList<>();
    protected boolean event;
    protected Map<UUID, Integer> scores = new HashMap<>();

    public void addScore(UUID uuid, int value) {
        int old = scores.getOrDefault(uuid, 0);
        scores.put(uuid, Math.max(0, old + value));
    }

    public int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }
}
