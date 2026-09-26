package fr.noltox.hcconfig;

import de.exlll.configlib.Serializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Serializer converting between Adventure {@link Component} and MiniMessage {@link String}
 * for ConfigLib.
 */
public final class HCComponentSerializer implements Serializer<Component, String> {

    /**
     * Singleton instance.
     */
    public static final HCComponentSerializer INSTANCE = new HCComponentSerializer();

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private HCComponentSerializer() {
    }

    @Override
    public String serialize(Component component) {
        return component == null ? "" : MINI_MESSAGE.serialize(component);
    }

    @Override
    public Component deserialize(String element) {
        return element == null ? Component.empty() : MINI_MESSAGE.deserialize(element);
    }
}
