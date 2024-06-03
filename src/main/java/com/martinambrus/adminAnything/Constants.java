package com.martinambrus.adminAnything;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Constants used across AdminAnything.
 *
 * @author Martin Ambrus
 */
public enum Constants implements Iterable<String> {

    /**
     * Regex to check if String is a number.
     */
    INT_REGEX(
        "(-)?(\\d){1,10}(\\.(\\d){1,10})?" //NON-NLS
            ),

    /**
     * A list of built-in Minecraft server commands, so we can say which ones
     * are being overridden by plug-ins.
     * -&gt; Spigot built-in server commands (taken from commandMap of Spigot 1.12).
     */
    @SuppressWarnings("HardCodedStringLiteral") BUILTIN_COMMANDS(
            Arrays.asList("?", "about", "achievement", "advancement", "ban", "ban-ip", "banlist", "blockdata", "clear",
                    "clone", "debug", "defaultgamemode", "deop", "difficulty", "effect", "enchant", "entitydata",
                    "execute", "fill", "function", "gamemode", "gamerule", "give", "help", "icanhasbukkit", "kick",
                    "kill", "list", "locate", "me", "op", "pardon", "pardon-ip", "particle", "pl", "playsound",
                    "plugins", "recipe", "reload", "replaceitem", "restart", "rl", "save-all", "save-off", "save-on",
                    "say", "scoreboard", "seed", "setblock", "setidletimeout", "setworldspawn", "spawnpoint", "spigot",
                    "spreadplayers", "stats", "stopsound", "stop", "summon", "teleport", "tell", "tellraw", "testfor",
                    "testforblock", "testforblocks", "time", "timings", "title", "toggledownfall", "tp", "tps",
                    "trigger", "ver", "version", "weather", "whitelist", "w", "worldborder", "xp")
            );

    /**
     * The string value of an ENUM.
     */
    private String constantValue;

    /**
     * The list of strings value of an ENUM.
     */
    private List<String> constantListValue;

    /**
     * Constructor for String ENUMs.
     * @param value String value for the ENUM.
     */
    Constants(final String value) {
        this.constantValue = value;
    } //end method

    /**
     * Constructor for list of strings ENUMs.
     * @param value List value for the ENUM.
     */
    Constants(final List<String> value) {
        this.constantListValue = value;
    } //end method

    /**
     * Returns the iterator for a list-type constant.
     *
     * @return Returns the iterator for a list-type constant.
     */
    @Override
    public Iterator<String> iterator() {
        return this.constantListValue.iterator();
    } //end method

    /* (non-Javadoc)
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString() {
        return this.constantValue;
    } // end method

    /**
     * Returns a list-type constant.
     *
     * @return Returns a List of strings which is the value of the given requested config element.
     */
    public List<String> getValues() {
        return Collections.unmodifiableList(this.constantListValue);
    } // end method

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        throw new java.io.NotSerializableException("com.martinambrus.adminAnything.Constants");
    }
} // end class