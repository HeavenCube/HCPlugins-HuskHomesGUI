package fr.noltox.hcplugins.huskhomesgui.command;

import fr.noltox.hcplugins.core.api.command.CoreCommand;
import fr.noltox.hcplugins.core.api.message.CoreTranslations;
import fr.noltox.hcplugins.huskhomesgui.gui.HomesMenu;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * Defines the canonical {@code /hcplugins huskhomesgui} branch.
 */
public final class HomesCommand implements CoreCommand {

    private static final Component USAGE = Component.text(
            "Utilisation : /hcplugins huskhomesgui reload",
            NamedTextColor.RED
    );
    private final HomesMenu homesMenu;
    private final Logger logger;
    private final Plugin plugin;
    private final CoreTranslations translations;

    public HomesCommand(Plugin plugin, CoreTranslations translations, HomesMenu homesMenu, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.translations = Objects.requireNonNull(translations, "translations");
        this.homesMenu = Objects.requireNonNull(homesMenu, "homesMenu");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            reload(source);
        } else {
            source.getSender().sendMessage(USAGE);
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!source.getSender().isOp() || args.length > 1) {
            return List.of();
        }
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return "reload".startsWith(prefix) ? List.of("reload") : List.of();
    }

    private void reload(CommandSourceStack source) {
        if (!source.getSender().isOp()) {
            source.getSender().sendMessage(translations.operatorOnly());
            return;
        }
        long started = System.nanoTime();
        try {
            homesMenu.reload();
            source.getSender().sendMessage(translations.reloadSuccess(plugin, System.nanoTime() - started));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Impossible de recharger la configuration de l'interface.", exception);
            source.getSender().sendMessage(translations.reloadFailure(plugin));
        }
    }
}
