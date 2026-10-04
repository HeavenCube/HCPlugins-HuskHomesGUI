package fr.noltox.hcplugins.huskhomesgui.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Small wrappers around Paper's native client dialogs.
 */
final class HomeDialogs {

    private HomeDialogs() {
    }

    static void text(
            Player player,
            BooleanSupplier active,
            Component title,
            Component label,
            Component confirmLabel,
            Component cancelLabel,
            String initialValue,
            int maxLength,
            Consumer<String> onConfirm
    ) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .inputs(List.of(DialogInput.text("value", label)
                                .initial(initialValue)
                                .maxLength(maxLength)
                                .width(300)
                                .build()))
                        .build())
                .type(DialogType.confirmation(
                        action(player, confirmLabel, Component.empty(), response -> {
                            String value = response.getText("value");
                            if (active.getAsBoolean() && player.isOnline() && value != null) {
                                onConfirm.accept(value.substring(0, Math.min(value.length(), maxLength)));
                            }
                        }),
                        action(player, cancelLabel, Component.empty(), response -> {
                            // Closing is intentionally a no-op.
                        })
                ))
        );
        show(player, dialog);
    }

    static void confirm(
            Player player,
            BooleanSupplier active,
            Component title,
            Component description,
            ItemStack displayItem,
            Component confirmLabel,
            Component cancelLabel,
            Runnable onConfirm
    ) {
        List<DialogBody> body = displayItem == null
                ? List.of(DialogBody.plainMessage(description))
                : List.of(DialogBody.item(displayItem)
                .description(DialogBody.plainMessage(description))
                .showDecorations(false)
                .showTooltip(false)
                .width(24)
                .height(24)
                .build());
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(body)
                        .build())
                .type(DialogType.confirmation(
                        action(player, confirmLabel, Component.empty(), response -> {
                            if (active.getAsBoolean() && player.isOnline()) {
                                onConfirm.run();
                            }
                        }),
                        action(player, cancelLabel, Component.empty(), response -> {
                            // Closing is intentionally a no-op.
                        })
                ))
        );
        show(player, dialog);
    }

    private static ActionButton action(
            Player player,
            Component label,
            Component tooltip,
            Consumer<io.papermc.paper.dialog.DialogResponseView> callback
    ) {
        return ActionButton.create(
                label,
                tooltip,
                100,
                DialogAction.customClick(
                        (view, audience) -> {
                            if (audience == player) {
                                callback.accept(view);
                            }
                        },
                        ClickCallback.Options.builder().uses(1).build()
                )
        );
    }

    private static void show(Player player, Dialog dialog) {
        player.showDialog(dialog);
    }
}
