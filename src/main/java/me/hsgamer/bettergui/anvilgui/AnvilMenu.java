package me.hsgamer.bettergui.anvilgui;

import me.hsgamer.bettergui.action.ActionApplier;
import me.hsgamer.bettergui.api.button.WrappedButton;
import me.hsgamer.bettergui.api.menu.StandardMenu;
import me.hsgamer.bettergui.builder.ButtonBuilder;
import me.hsgamer.bettergui.button.WrappedDummyButton;
import me.hsgamer.bettergui.util.ProcessApplierConstants;
import me.hsgamer.bettergui.util.StringReplacerApplier;
import me.hsgamer.hscore.bukkit.gui.object.BukkitItem;
import me.hsgamer.hscore.bukkit.scheduler.Scheduler;
import me.hsgamer.hscore.collections.map.CaseInsensitiveStringMap;
import me.hsgamer.hscore.common.CollectionUtils;
import me.hsgamer.hscore.common.StringReplacer;
import me.hsgamer.hscore.config.CaseInsensitivePathString;
import me.hsgamer.hscore.config.Config;
import me.hsgamer.hscore.minecraft.gui.object.Item;
import me.hsgamer.hscore.task.BatchRunnable;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static me.hsgamer.bettergui.BetterGUI.getInstance;

public class AnvilMenu extends StandardMenu {
    private final Map<UUID, AnvilGUI> anvilGUIList = new ConcurrentHashMap<>();
    private final Map<UUID, String> userInputs = new ConcurrentHashMap<>();
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
        variableManager.register("anvil_input", StringReplacer.of((original, uuid) -> userInputs.getOrDefault(uuid, "")));

        Optional.ofNullable(menuSettings.get("title")).map(String::valueOf).ifPresent(string -> this.title = string);
        Optional.ofNullable(menuSettings.get("text")).map(String::valueOf).ifPresent(string -> this.text = string);
        this.completeAction = Optional.ofNullable(menuSettings.get("complete-action")).map(o -> new ActionApplier(this, o)).orElse(completeAction);
        this.closeAction = Optional.ofNullable(menuSettings.get("close-action")).map(o -> new ActionApplier(this, o)).orElse(closeAction);
        Optional.ofNullable(menuSettings.get("prevent-close")).map(String::valueOf).map(Boolean::parseBoolean).ifPresent(bool -> this.preventClose = bool);
        Optional.ofNullable(menuSettings.get("clear-input-on-complete")).map(String::valueOf).map(Boolean::parseBoolean).ifPresent(bool -> this.clearInput = bool);
        Optional.ofNullable(menuSettings.get("command"))
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

        for (Map.Entry<CaseInsensitivePathString, Object> entry : configSettings.entrySet()) {
            CaseInsensitivePathString key = entry.getKey();
            Object value = entry.getValue();
            if (!(value instanceof Map)) {
                continue;
            }
            //noinspection unchecked
            Map<String, Object> values = new CaseInsensitiveStringMap<>((Map<String, Object>) value);
            if (key.equals(new CaseInsensitivePathString("left-button"))) {
                leftButton = ButtonBuilder.INSTANCE.build(new ButtonBuilder.Input(this, "left_button", values)).orElse(null);
                if (leftButton != null) {
                    leftButton.init();
                }
            } else if (key.equals(new CaseInsensitivePathString("right-button"))) {
                rightButton = ButtonBuilder.INSTANCE.build(new ButtonBuilder.Input(this, "right_button", values)).orElse(null);
                if (rightButton != null) {
                    rightButton.init();
                }
            } else {
                button = new WrappedDummyButton(new ButtonBuilder.Input(this, "button", values));
                button.init();
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
        builder.onClose(stateSnapshot -> {
            BatchRunnable batchRunnable = new BatchRunnable();
            batchRunnable
                    .getTaskPool(ProcessApplierConstants.ACTION_STAGE)
                    .addLast(process -> closeAction.accept(stateSnapshot.getPlayer().getUniqueId(), process));
            Scheduler.current().async().runTask(batchRunnable);
        });
        builder.mainThreadExecutor(runnable -> Scheduler.current().sync().runEntityTask(player, runnable));
        builder.onClickAsync((slot, stateSnapshot) -> {
            if (slot != AnvilGUI.Slot.OUTPUT) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            userInputs.put(stateSnapshot.getPlayer().getUniqueId(), stateSnapshot.getText());

            BatchRunnable batchRunnable = new BatchRunnable();
            batchRunnable
                    .getTaskPool(ProcessApplierConstants.ACTION_STAGE)
                    .addLast(process -> completeAction.accept(stateSnapshot.getPlayer().getUniqueId(), process));
            if (clearInput) {
                batchRunnable
                        .getTaskPool(ProcessApplierConstants.ACTION_STAGE + 1)
                        .addLast(() -> userInputs.remove(stateSnapshot.getPlayer().getUniqueId()));
            }

            return CompletableFuture.completedFuture(Arrays.asList(
                    AnvilGUI.ResponseAction.run(batchRunnable),
                    AnvilGUI.ResponseAction.run(() -> remove(stateSnapshot.getPlayer().getUniqueId(), false)),
                    AnvilGUI.ResponseAction.close()
            ));
        });

        if (title != null) {
            builder.title(StringReplacerApplier.replace(title, player.getUniqueId(), this));
        }

        if (preventClose) {
            builder.preventClose();
        }

        if (text != null) {
            builder.text(StringReplacerApplier.replace(text, player.getUniqueId(), this));
        }

        if (button != null) {
            getItem(button.getItem(player.getUniqueId())).ifPresent(builder::itemOutput);
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

        if (leftButton != null) {
            leftButton.stop();
        }

        if (rightButton != null) {
            rightButton.stop();
        }

        if (button != null) {
            button.stop();
        }
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
