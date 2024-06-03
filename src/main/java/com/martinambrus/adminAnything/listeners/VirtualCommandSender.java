package com.martinambrus.adminAnything.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * A virtual command sender which extends the base CommandSender
 * and used when redirecting commands to other commands. Since the original
 * command gets cancelled, we need a virtual command sender to send the new one.
 *
 * This class is also used when muting commands that can be muted this way.
 *
 * @author Martin Ambrus
 */
final class VirtualCommandSender implements SpigotCommandSender {

    /**
     * Stores messages sent by players that should be muted.
     */
    private List<String> lastMessages;

    /**
     * The virtual sender's name.
     */
    private final String name = "[AdminAnything VirtualPlayer]"; //NON-NLS

    /**
     * Permissions information - to be copied from the original sender.
     */
    private Set<PermissionAttachmentInfo> perms;

    /**
     * Whether or not this command sender is an operator.
     */
    private boolean op   = false;

    @Override
    public PermissionAttachment addAttachment(final Plugin arg0) {
        return null;
    } // end method

    @Override
    public PermissionAttachment addAttachment(final Plugin arg0, final int arg1) {
        return null;
    } // end method

    @Override
    public PermissionAttachment addAttachment(final Plugin arg0, final String arg1, final boolean arg2) {
        return null;
    } // end method

    @Override
    public PermissionAttachment addAttachment(final Plugin arg0, final String arg1, final boolean arg2,
            final int arg3) {
        return null;
    } // end method

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return perms;
    } // end method

    public void setEffectivePermissions(final Set<PermissionAttachmentInfo> p) {
        perms = p;
    } // end method

    @Override
    public boolean hasPermission(final String arg0) {
        for (final PermissionAttachmentInfo perm : perms) {
            if (perm.getPermission().equals(arg0)) {
                return true;
            }
        }

        return false;
    } // end method

    @Override
    public boolean hasPermission(final Permission arg0) {
        for (final PermissionAttachmentInfo perm : perms) {
            if (perm.getPermission().equals(arg0.getName())) {
                return true;
            }
        }

        return false;
    } // end method

    @Override
    public boolean isPermissionSet(final String arg0) {
        for (final PermissionAttachmentInfo perm : perms) {
            if (perm.getPermission().equals(arg0)) {
                return true;
            }
        }

        return false;
    } // end method

    @Override
    public boolean isPermissionSet(final Permission arg0) {
        for (final PermissionAttachmentInfo perm : perms) {
            if (perm.getPermission().equals(arg0.getName())) {
                return true;
            }
        }

        return false;
    } // end method

    @Override
    public void recalculatePermissions() {
    } // end method

    @Override
    public void removeAttachment(final PermissionAttachment arg0) {
    } // end method

    @Override
    public boolean isOp() {
        return op;
    } // end method

    @Override
    public void setOp(final boolean arg0) {
        op = arg0;
    } // end method

    @Override
    public String getName() {
        return name;
    } // end method

    @Override
    public Server getServer() {
        return Bukkit.getServer();
    } // end method

    @Override
    public void sendMessage(final String arg0) {
        if (null == lastMessages) {
            lastMessages = new ArrayList<String>();
        }

        lastMessages.add(arg0);
    } // end method

    @Override
    public void sendMessage(final String[] arg0) {
        if (null == lastMessages) {
            lastMessages = new ArrayList<String>();
        }

        Collections.addAll(lastMessages, arg0);
    } // end method

    /**
     * Sends this sender a message
     *
     * @param sender  The sender of this message
     * @param message Message to be displayed
     */
    @Override
    public void sendMessage(UUID sender, String message) {
        if (null == lastMessages) {
            lastMessages = new ArrayList<String>();
        }

        lastMessages.add(message);
    }

    /**
     * Sends this sender multiple messages
     *
     * @param sender   The sender of this message
     * @param messages An array of messages to be displayed
     */
    @Override
    public void sendMessage(UUID sender, String[] messages) {
        if (null == lastMessages) {
            lastMessages = new ArrayList<String>();
        }

        Collections.addAll(lastMessages, messages);
    }

    public List<String> getLastMessage() {
        return null == lastMessages ? new ArrayList<String>() : lastMessages;
    } // end method

    @Override
    public Spigot spigot() {
        return null;
    } // end method

} // end class