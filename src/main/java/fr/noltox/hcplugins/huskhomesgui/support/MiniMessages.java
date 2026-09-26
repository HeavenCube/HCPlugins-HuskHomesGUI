package fr.noltox.hcplugins.huskhomesgui.support;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Objects;

/** Shared MiniMessage entry points for configurable text. */
public final class MiniMessages {

    private static final MiniMessage STANDARD = MiniMessage.miniMessage();
    private static final MiniMessage STRICT = MiniMessage.builder().strict(true).build();

    private MiniMessages() {
    }

    public static Component parse(String template, TagResolver... resolvers) {
        return STANDARD.deserialize(Objects.requireNonNull(template, "template"), resolvers);
    }

    public static Component parseStrict(String template, TagResolver... resolvers) {
        return STRICT.deserialize(Objects.requireNonNull(template, "template"), resolvers);
    }
}
