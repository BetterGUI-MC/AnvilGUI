package me.hsgamer.bettergui.anvilgui;

import static me.hsgamer.bettergui.BetterGUI.getInstance;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.hsgamer.bettergui.builder.IconBuilder;
import me.hsgamer.bettergui.object.Icon;
import me.hsgamer.bettergui.object.LocalVariable;
import me.hsgamer.bettergui.object.LocalVariableManager;
import me.hsgamer.bettergui.object.Menu;
import me.hsgamer.bettergui.object.property.menu.MenuAction;
import me.hsgamer.bettergui.object.property.menu.MenuTitle;
import me.hsgamer.bettergui.util.CommonUtils;
import net.md_5.bungee.api.ChatColor;
import net.wesjd.anvilgui.AnvilGUI;
import net.wesjd.anvilgui.AnvilGUI.Builder;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class AnvilMenu extends Menu<AnvilGUI> {

  private final Map<UUID, AnvilGUI> anvilGUIList = new ConcurrentHashMap<>();
  private final Map<UUID, String> userInputs = new HashMap<>();
  private MenuTitle title;
  private String text;
  private MenuAction completeAction;
  private MenuAction closeAction;
  private Icon icon;
  private boolean preventClose;
  private boolean clearInput = false;

  public AnvilMenu(String name) {
    super(name);
  }

  @Override
  public void setFromFile(FileConfiguration fileConfiguration) {
    fileConfiguration.getKeys(false).forEach(key -> {
      if (key.equalsIgnoreCase("menu-settings")) {
        ConfigurationSection section = fileConfiguration.getConfigurationSection("menu-settings");
        section.getKeys(false).forEach(key1 -> {
          if (key1.equalsIgnoreCase("title")) {
            title = new MenuTitle(this);
            title.setValue(section.get(key1));
          } else if (key1.equalsIgnoreCase("text")) {
            text = section.getString(key1);
          } else if (key1.equalsIgnoreCase("complete-action")) {
            completeAction = new MenuAction(this);
            completeAction.setValue(section.get(key1));
          } else if (key1.equalsIgnoreCase("close-action")) {
            closeAction = new MenuAction(this);
            closeAction.setValue(section.get(key1));
          } else if (key1.equalsIgnoreCase("prevent-close")) {
            preventClose = section.getBoolean(key1);
          } else if (key1.equalsIgnoreCase("command")) {
            CommonUtils.createStringListFromObject(section.get(key1), true)
                .forEach(s -> {
                  if (s.contains(" ")) {
                    getInstance().getLogger().warning(
                        "Illegal characters in command '" + s + "'" + "in the menu '" + getName()
                            + "'. Ignored");
                  } else {
                    getInstance().getCommandManager().registerMenuCommand(s, this);
                  }
                });
          } else if (key1.equalsIgnoreCase("clear-input-on-complete")) {
            clearInput = section.getBoolean(key1);
          }
        });
      } else {
        icon = IconBuilder.getIcon(this, fileConfiguration.getConfigurationSection(key));
      }
    });

    registerVariable("anvil_input", new LocalVariable() {
      @Override
      public String getIdentifier() {
        return "anvil_input";
      }

      @Override
      public LocalVariableManager<?> getInvolved() {
        return getParent();
      }

      @Override
      public String getReplacement(OfflinePlayer offlinePlayer, String s) {
        return userInputs.getOrDefault(offlinePlayer.getUniqueId(), "");
      }
    });
  }

  @Override
  public boolean createInventory(Player player, String[] strings, boolean b) {
    AnvilGUI.Builder builder = new Builder().plugin(getInstance());
    if (closeAction != null) {
      builder.onClose(player1 -> closeAction.getParsed(player1).execute());
    }

    builder.onComplete((player1, s) -> {
      userInputs.put(player1.getUniqueId(), ChatColor.stripColor(s));
      if (completeAction != null) {
        completeAction.getParsed(player1).execute(() -> {
          if (clearInput) {
            userInputs.remove(player1.getUniqueId());
          }
        });
      } else if (clearInput) {
        userInputs.remove(player1.getUniqueId());
      }
      remove(player1.getUniqueId(), false);
      return AnvilGUI.Response.close();
    });

    if (title != null) {
      builder.title(title.getParsed(player));
    }

    if (preventClose) {
      builder.preventClose();
    }

    if (text != null) {
      builder.text(
          CommonUtils.colorize(hasVariables(player, text) ? setVariables(text, player) : text));
    }

    if (icon != null) {
      icon.createClickableItem(player)
          .ifPresent(clickableItem -> builder.item(clickableItem.getItem()));
    }

    anvilGUIList.put(player.getUniqueId(), builder.open(player));
    return true;
  }

  @Override
  public void updateInventory(Player player) {
    // Ignored
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
  public Optional<AnvilGUI> getInventory(Player player) {
    return Optional.ofNullable(anvilGUIList.get(player.getUniqueId()));
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
