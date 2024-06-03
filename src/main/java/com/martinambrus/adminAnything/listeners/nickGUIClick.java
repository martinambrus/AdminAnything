package com.martinambrus.adminAnything.listeners;

import com.martinambrus.adminAnything.AA_API;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Listens to a player clicking on a chest GUI item
 * and executes the commands provided in lore of those items.
 *
 * @author Martin Ambrus
 */
public class nickGUIClick implements Listener {

    /**
     * Name of this feature, used in checking for it being
     * enabled or disabled on the server.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    final String featureName = "chatnickgui";

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Constructor, stores instance of AdminAnything for further use.
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public nickGUIClick(final Plugin aa) {
        this.plugin = aa;

        // reload config values
        AA_API.loadNickGUIItems();
    } // end method

    /***
     * Handles clicking on a chest GUI item.
     *
     * @param event The actual join event to work with.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void handleGUIItemClick(final InventoryClickEvent event) {
        // check that a player clicked the chest GUI inventory of AdminAnything
        if ( event.getWhoClicked() instanceof Player ) {
            Player player = (Player) event.getWhoClicked();
            if ( event.getInventory() != null ) {
                String inventoryTitle = event.getView().getTitle();
                if ( inventoryTitle.startsWith( AA_API.__("gui.title") ) || inventoryTitle.equals( player.getName() ) || inventoryTitle.equals( "[A]" ) ) {
                    // get the lore
                    ItemStack stack  = event.getCurrentItem();
                    if ( null == stack ) {
                        return;
                    }

                    ItemMeta     meta = stack.getItemMeta();

                    if ( null == meta ) {
                        return;
                    }

                    List<String> lore = meta.getLore();

                    // check that we've got the correct lore
                    if ( null == lore || !lore.get( 0 ).startsWith( AA_API.__("gui.will-run-command") ) ) {
                        return;
                    }

                    // we've got the correct chest GUI click, let's run the command from lore
                    event.setCancelled(true);

                    // remove the lang file text and leave only command to run
                    String cmd = lore.get( 0 ).replace( AA_API.__("gui.will-run-command") + " ", "" );

                    // if the command starts with /, remove that
                    if ( cmd.startsWith("/") ) {
                        cmd = cmd.substring( 1 );
                    }

                    // close the GUI, if set in config
                    if ( AA_API.getConfigBoolean("nickGUICloseAfterRunningCommand") ) {
                        player.closeInventory();
                    }

                    // run this command as the player clicking on this item
                    // but do it after the initial inventory was closed, so other inventory-opening commands
                    // can still work with this GUI
                    Bukkit.dispatchCommand(player, cmd);
                }
            }
        }
    } // end method

} // end class