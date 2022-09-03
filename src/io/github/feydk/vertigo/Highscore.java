package io.github.feydk.vertigo;

import com.cavetale.core.playercache.PlayerCache;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class Highscore {
    public static final Highscore ZERO = new Highscore(new UUID(0L, 0L), 0);
    public final UUID uuid;
    public final int score;
    protected int placement;

    public Component name() {
        return this != ZERO ? text(PlayerCache.nameForUuid(uuid), WHITE) : text("???", GRAY);
    }
}
