package fr.noltox.hcplugins.huskhomesgui.gui;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One icon in one viewer's menu. InvUI requests it on the server thread when
 * its page is displayed. Reopening/filtering creates new icons; no global cache.
 */
final class LazyMenuIcon<T> implements Function<Player, T> {

    private Supplier<T> renderer;
    private T rendered;

    LazyMenuIcon(Supplier<T> renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public T apply(Player ignoredViewer) {
        if (rendered == null) {
            rendered = Objects.requireNonNull(renderer.get(), "rendered icon");
            renderer = null;
        }
        return rendered;
    }
}
