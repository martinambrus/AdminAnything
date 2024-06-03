package com.martinambrus.adminAnything;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.Map;

/**
 * Inventory-related tooling. Mostly used to present a chest GUI
 * for player actions.
 *
 * @author Martin Ambrus
 */
final class InventoryManager {

    /**
     * Instance of {@link AdminAnything}.
     */
    private final AdminAnything aa;

    /**
     * Constructor. Stores our plugin's reference locally and starts GUI clicks listener.
     *
     * @param plugin Instance of {@link AdminAnything AdminAnything}.
     */
    InventoryManager(final AdminAnything plugin) {
        aa = plugin;

        if ( AA_API.isFeatureEnabled("chatnickgui") ) {
            AA_API.startRequiredListener( "nickGUIClick" ); //NON-NLS
        }
    } //end method

    /**
     * Creates a chest GUI with commands runnable for the given player.
     *
     * @param player Player to create the chest GUI for.
     * @param playerNameForCommands Player name to be used in all the commands in the GUI
     *                              instead of a %PLAYER% placeholder.
     */
    Inventory createGUIPlayerInventory(Player player, String playerNameForCommands) {
        int inventorySize = 54;
        int current_index = 0;
        String inventoryName = AA_API.__("gui.title") + " " + playerNameForCommands;
        // check inventory title and shorten it to player's name only, if it's too long
        if ( inventoryName.length() > 32 ) {
            // the player name is actually too long, so use the GUI title from config only
            if ( playerNameForCommands.length() > 32 ) {
                inventoryName = AA_API.__("gui.title");
            } else {
                inventoryName = playerNameForCommands;
            }

            // if we've used custom lang file with GUI title too long, replace it with [A]
            if ( inventoryName.length() > 32 ) {
                inventoryName = "[A]";
            }
        }

        Inventory inventory = Bukkit.createInventory(null, inventorySize, inventoryName);

        // fill the items in inventory with command-representing items from config
        for ( final Map.Entry<String, Map<String, String>> pair : AA_API.getGUIItemsMap().entrySet() ) {
            // check that the player has a valid permissio to see this item, if a permission was set
            if ( null != pair.getValue().get("permission") && !AA_API.checkPerms( player, pair.getValue().get("permission"), false ) ) {
                continue;
            }

            // verify the material for this config item
            Material mat = null;

            if ( null != pair.getValue().get("item") ) {

                if ( null == mat ) {
                    mat = Material.getMaterial( pair.getValue().get("item").toUpperCase() );
                }

                if ( null == mat ) {
                    mat = Material.getMaterial( "LEGACY_" + pair.getValue().get("item").toUpperCase() );
                }

            }

            // if material was not found, use player head
            if ( null == mat ) {
                mat = Material.getMaterial("PLAYER_HEAD");
            }

            // PLAYER_HEAD was added in 1.13, so make this a golden carrot in 1.7+
            if ( null == mat ) {
                mat = Material.getMaterial("GOLDEN_CARROT");
            }

            // create the actual item stack and give it lore, so we can easily read the command from it
            ItemStack stack = new ItemStack( mat );
            ItemMeta  meta = stack.getItemMeta();

            // set title for the material, if provided
            if ( null != pair.getValue().get("title") ) {
                meta.setDisplayName( pair.getValue().get("title").replace("%PLAYER%", playerNameForCommands) );
            }

            // set the correct lore
            meta.setLore(
                Collections.singletonList(
                    AA_API.__("gui.will-run-command")
                        + " "
                        + pair.getValue().get("command").replace("%PLAYER%", playerNameForCommands)
                )
            );
            stack.setItemMeta(meta);
            inventory.setItem(current_index++, stack);

            // if we'd go overboard with commands and put more than 54 into the config,
            // let's warn the owner through console
            if ( current_index == 55 ) {
                Bukkit.getLogger().warning(AA_API.__("gui.too-many-items"));
                break;
            }
        }

        return inventory;
    }

} // end class