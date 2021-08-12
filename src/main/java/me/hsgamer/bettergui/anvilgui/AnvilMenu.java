package me.hsgamer.bettergui.anvilgui;

import me.hsgamer.bettergui.BetterGUI;
import me.hsgamer.bettergui.api.action.Action;
import me.hsgamer.bettergui.api.button.WrappedButton;
import me.hsgamer.bettergui.api.menu.Menu;
import me.hsgamer.bettergui.builder.ActionBuilder;
import me.hsgamer.bettergui.builder.ButtonBuilder;
import me.hsgamer.bettergui.button.DummyButton;
import me.hsgamer.bettergui.lib.core.bukkit.utils.MessageUtils;
import me.hsgamer.bettergui.lib.core.collections.map.CaseInsensitiveStringMap;
import me.hsgamer.bettergui.lib.core.common.CollectionUtils;
import me.hsgamer.bettergui.lib.core.config.Config;
import me.hsgamer.bettergui.lib.core.variable.VariableManager;
import me.hsgamer.bettergui.lib.taskchain.TaskChain;
import me.hsgamer.bettergui.manager.PluginVariableManager;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static me.hsgamer.bettergui.BetterGUI.getInstance;

public class AnvilMenu extends Menu {
    private final Map<UUID, AnvilGUI> anvilGUIList = new ConcurrentHashMap<>();
    private final Map<UUID, String> userInputs = new HashMap<>();
    private final List<Action> completeAction = new LinkedList<>();
    private final List<Action> closeAction = new LinkedList<>();
    private String title;
    private String text;
    private DummyButton button;
    private WrappedButton leftButton;
    private WrappedButton rightButton;
    private boolean preventClose = false;
    private boolean clearInput = false;

    public AnvilMenu(String name) {
        super(name);
        PluginVariableManager.register("menu_" + name + "_anvil_input", (original, uuid) -> userInputs.getOrDefault(uuid, ""));
    }

    @Override
    public void setFromConfig(Config config) {
        config.getNormalizedValues(false).forEach((key, value) -> {
            if (!(value instanceof Map)) {
                return;
            }
            Map<String, Object> values = new CaseInsensitiveStringMap<>((Map<String, Object>) value);
            if (key.equalsIgnoreCase("menu-settings")) {
                Optional.ofNullable(values.get("title")).map(String::valueOf).ifPresent(string -> this.title = string);
                Optional.ofNullable(values.get("text")).map(String::valueOf).ifPresent(string -> this.text = string);
                Optional.ofNullable(values.get("complete-action")).ifPresent(o -> this.completeAction.addAll(ActionBuilder.INSTANCE.getActions(this, o)));
                Optional.ofNullable(values.get("close-action")).ifPresent(o -> this.closeAction.addAll(ActionBuilder.INSTANCE.getActions(this, o)));
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
                leftButton = ButtonBuilder.INSTANCE.getButton(this, "menu_" + getName() + "_left_button", values);
            } else if (key.equalsIgnoreCase("right-button")) {
                rightButton = ButtonBuilder.INSTANCE.getButton(this, "menu_" + getName() + "_right_button", values);
            } else {
                button = new DummyButton(this);
                button.setName("menu_" + getName() + "_button");
                button.setFromSection(values);
            }
        });
    }

    @Override
    public boolean createInventory(Player player, String[] strings, boolean b) {
        AnvilGUI.Builder builder = new AnvilGUI.Builder().plugin(getInstance());
        builder.onClose(player1 -> {
            TaskChain<?> taskChain = BetterGUI.newChain();
            closeAction.forEach(action -> action.addToTaskChain(player1.getUniqueId(), taskChain));
            taskChain.execute();
        });

        builder.onComplete((player1, s) -> {
            userInputs.put(player1.getUniqueId(), s);
            TaskChain<?> taskChain = BetterGUI.newChain();
            completeAction.forEach(action -> action.addToTaskChain(player1.getUniqueId(), taskChain));
            taskChain.execute();
            if (clearInput) {
                userInputs.remove(player1.getUniqueId());
            }
            remove(player1.getUniqueId(), false);
            return AnvilGUI.Response.close();
        });

        if (title != null) {
            builder.title(VariableManager.setVariables(title, player.getUniqueId()));
        }

        if (preventClose) {
            builder.preventClose();
        }

        if (text != null) {
            builder.text(MessageUtils.colorize(VariableManager.setVariables(text, player.getUniqueId())));
        }

        if (button != null) {
            builder.itemLeft(button.getItemStack(player.getUniqueId()));
        }

        if (leftButton != null) {
            builder.itemLeft(leftButton.getItemStack(player.getUniqueId()));
        }

        if (rightButton != null) {
            builder.itemRight(rightButton.getItemStack(player.getUniqueId()));
        }

        anvilGUIList.put(player.getUniqueId(), builder.open(player));
        return true;
    }

    @Override
    public void updateInventory(Player player) {
        // EMPTY
    }

    @Override
    public void closeInventory(Player player) {
        remove(player.getUniqueId(), true);
    }

    @Override
    public void closeAll() {
        anvilGUIList.keySet().forEach(uuid -> remove(uuid, true));
        anvilGUIList.clear();
    }

    @Override
    public Object getOriginal() {
        return anvilGUIList;
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
