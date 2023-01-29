package me.hsgamer.bettergui.anvilgui;

import me.hsgamer.bettergui.BetterGUI;
import me.hsgamer.bettergui.action.ActionApplier;
import me.hsgamer.bettergui.api.button.WrappedButton;
import me.hsgamer.bettergui.api.menu.Menu;
import me.hsgamer.bettergui.builder.ButtonBuilder;
import me.hsgamer.bettergui.button.WrappedDummyButton;
import me.hsgamer.bettergui.util.ProcessApplierConstants;
import me.hsgamer.hscore.bukkit.gui.object.BukkitItem;
import me.hsgamer.hscore.bukkit.utils.MessageUtils;
import me.hsgamer.hscore.collections.map.CaseInsensitiveStringMap;
import me.hsgamer.hscore.common.CollectionUtils;
import me.hsgamer.hscore.config.Config;
import me.hsgamer.hscore.minecraft.gui.object.Item;
import me.hsgamer.hscore.variable.VariableManager;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static me.hsgamer.bettergui.BetterGUI.getInstance;

public class AnvilMenu extends Menu {
    private final Map<UUID, AnvilGUI> anvilGUIList = new ConcurrentHashMap<>();
    private final Map<UUID, String> userInputs = new HashMap<>();
    private ActionApplier completeAction = new ActionApplier(Collections.emptyList());
    private ActionApplier closeAction = new ActionApplier(Collections.emptyList());
    private String title;
    private String text;
    private WrappedButton button;
    private WrappedButton leftButton;
    private WrappedButton rightButton;
    private boolean preventClose = false;
    private boolean clearInput = false;

    protected AnvilMenu(Config config) {
        super(config);
        variableManager.register("anvil_input", (original, uuid) -> userInputs.getOrDefault(uuid, ""));
        for (Map.Entry<String, Object> entry : config.getNormalizedValues(false).entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!(value instanceof Map)) {
                continue;
            }
            //noinspection unchecked
            Map<String, Object> values = new CaseInsensitiveStringMap<>((Map<String, Object>) value);
            if (key.equalsIgnoreCase("menu-settings")) {
                Optional.ofNullable(values.get("title")).map(String::valueOf).ifPresent(string -> this.title = string);
                Optional.ofNullable(values.get("text")).map(String::valueOf).ifPresent(string -> this.text = string);
                this.completeAction = Optional.ofNullable(values.get("complete-action")).map(o -> new ActionApplier(this, o)).orElse(completeAction);
                this.closeAction = Optional.ofNullable(values.get("close-action")).map(o -> new ActionApplier(this, o)).orElse(closeAction);
                Optional.ofNullable(values.get("prevent-close")).map(String::valueOf).map(Boolean::parseBoolean).ifPresent(bool -> this.preventClose = bool);
                Optional.ofNullable(values.get("clear-input-on-complete")).map(String::valueOf).map(Boolean::parseBoolean).ifPresent(bool -> this.clearInput = bool);
                Optional.ofNullable(values.get("command"))
                        .map(o -> CollectionUtils.createStringListFromObject(o, true))
                        .ifPresent(list -> {
                            for (String s : list) {
                                if (s.contains(" ")) {
                                    getInstance().getLogger().warning("Illegal characters in command '" + s + "'" + "in the menu '" + getName() + "'. Ignored");
                                } else {
                                    getInstance().getMenuCommandManager().registerMenuCommand(s, this);
                                }
                            }
                        });
            } else if (key.equalsIgnoreCase("left-button")) {
                leftButton = ButtonBuilder.INSTANCE.build(new ButtonBuilder.Input(this, "left_button", values)).orElse(null);
            } else if (key.equalsIgnoreCase("right-button")) {
                rightButton = ButtonBuilder.INSTANCE.build(new ButtonBuilder.Input(this, "right_button", values)).orElse(null);
            } else {
                button = new WrappedDummyButton(new ButtonBuilder.Input(this, "button", values));
            }
        }
    }

    private static Optional<ItemStack> getItem(Item item) {
        if (item instanceof BukkitItem) {
            return Optional.of(((BukkitItem) item).getItemStack());
        }
        return Optional.empty();
    }

    @Override
    public boolean create(Player player, String[] strings, boolean bypass) {
        AnvilGUI.Builder builder = new AnvilGUI.Builder().plugin(getInstance());
        builder.onClose(player1 -> BetterGUI.runBatchRunnable(batchRunnable ->
                batchRunnable
                        .getTaskPool(ProcessApplierConstants.ACTION_STAGE)
                        .addLast(process -> closeAction.accept(player1.getUniqueId(), process))
        ));

        builder.onComplete((player1, s) -> {
            userInputs.put(player1.getUniqueId(), s);
            BetterGUI.runBatchRunnable(batchRunnable -> {
                batchRunnable
                        .getTaskPool(ProcessApplierConstants.ACTION_STAGE)
                        .addLast(process -> completeAction.accept(player1.getUniqueId(), process));
                if (clearInput) {
                    batchRunnable
                            .getTaskPool(ProcessApplierConstants.ACTION_STAGE + 1)
                            .addLast(process -> userInputs.remove(player1.getUniqueId()));
                }
            });
            remove(player1.getUniqueId(), false);
            return AnvilGUI.Response.close();
        });

        if (title != null) {
            builder.title(replace(title, player.getUniqueId()));
        }

        if (preventClose) {
            builder.preventClose();
        }

        if (text != null) {
            builder.text(MessageUtils.colorize(VariableManager.setVariables(text, player.getUniqueId())));
        }

        if (button != null) {
            getItem(button.getItem(player.getUniqueId())).ifPresent(builder::itemLeft);
        }

        if (leftButton != null) {
            getItem(leftButton.getItem(player.getUniqueId())).ifPresent(builder::itemLeft);
        }

        if (rightButton != null) {
            getItem(rightButton.getItem(player.getUniqueId())).ifPresent(builder::itemRight);
        }

        anvilGUIList.put(player.getUniqueId(), builder.open(player));
        return true;
    }

    @Override
    public void update(Player player) {
        // EMPTY
    }

    @Override
    public void close(Player player) {
        remove(player.getUniqueId(), true);
    }

    @Override
    public void closeAll() {
        anvilGUIList.keySet().forEach(uuid -> remove(uuid, true));
        anvilGUIList.clear();
    }

    private void remove(UUID uuid, boolean closeInventory) {
        anvilGUIList.computeIfPresent(uuid, (uuid1, anvilGUI) -> {
            if (closeInventory) {
                anvilGUI.closeInventory();
            }
            return null;
        });
    }
}
