package me.rukon0621.rknpc.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Components {

    private Components() {
    }

    public static Component parse(String input) {
        if (input.contains("<") && input.contains(">")) {
            return MiniMessage.miniMessage().deserialize(input);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(input);
    }
}
