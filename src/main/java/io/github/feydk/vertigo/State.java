package io.github.feydk.vertigo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class State {
    protected boolean event;
    protected boolean pause;
    protected Map<UUID, Integer> scores = new HashMap<>();

    public void addScore(UUID uuid, int value) {
        int old = scores.getOrDefault(uuid, 0);
        scores.put(uuid, Math.max(0, old + value));
    }

    public int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }
}
