package com.martinambrus.adminAnything;

import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bukkit.ChatColor.COLOR_CHAR;

/**
 * Various common utilities class used thorough the whole AA plugin.
 *
 * @author Martin Ambrus
 */
public enum Utils {
    ;

    /**
     * The actual (cached) version of the server we're running.
     */
    private static String  mcVersion;

    /**
     * Returns current unix timestamp.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * // ... as used in /aa_mutecommand ...
     * // check if this class should still be muted
     * if ((mutedClasses.get(newMutedClass) + maxMuteCheckTimeout) > Utils.getUnixTimestamp()) {
     *     lastClassMuted = true;
     * }
     * }
     * </pre>
     *
     * @return Returns current unix timestamp as an Integer.
     */
    public static int getUnixTimestamp() {
        return getUnixTimestamp(0L);
    } // end method

    /***
     * Converts given timestamp in milliseconds to Unix Timestamp in seconds.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // ... as used in the commandPreprocessor event listener ...
     * Aa_mutecommand.lastMuteTimestamp = Utils.getUnixTimestamp(0L);
     * </pre>
     *
     * @param i Integer timestamp in milliseconds to convert.
     * @return Returns Unix Timestamp in seconds.
     */
    public static int getUnixTimestamp(Long i) {
        if (0 == i) {
            i = System.currentTimeMillis();
        }

        //noinspection IntegerDivisionInFloatingPointContext
        return (int) Math.floor(i / 1000L);
    } // end method

    /**
     * Grabs the numerical version portion of the full server's version.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * String serverVersion = Utils.getMinecraftVersion();
     * </pre>
     *
     * @return Returns server version as a string (for example "1.11.2").
     */
    public static String getMinecraftVersion() {
        return getMinecraftVersion(Bukkit.getServer().getBukkitVersion());
    } // end method

    /**
     * Grabs the numerical version portion of the full server's version.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * Utils.getMinecraftVersion(Bukkit.getServer().getBukkitVersion());
     * </pre>
     *
     * @param serverVersion An optional version string to extract the version from.
     *
     * @return Returns server version as a string (for example "1.11.2").
     */
    public static String getMinecraftVersion(final String serverVersion) {
        if (null != mcVersion) {
            return mcVersion;
        }

        final Pattern pattern = Pattern.compile("\\(.*?\\) ?");
        final Matcher matcher = pattern.matcher(serverVersion);
        String regex = null;
        if (matcher.find()) {
            //noinspection Annotator
            regex = matcher.group(0).replaceAll("\\([M][C][:][\" \"]", "").replace(')', ' ').trim(); //NON-NLS
        }

        if (null == regex) {
            try {
                regex = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
            } catch (final IndexOutOfBoundsException e) {
                regex = serverVersion.replace('.', '_');
            }
        }

        mcVersion = regex;

        return mcVersion;
    } // end method

    /**
     * Takes an unmodifiable list and turns it into a modifiable one.
     *
     * @param theList The list we want to clone and make modifiable.
     *
     * @return Returns an ordinary cloned, modifiable list.
     */
    public static List<String> makeListMutable(Iterable<String> theList) {
        return new ArrayList<String>((Collection<String>) theList);
    } // end method

    /***
     * Takes command arguments that may contain quotes and will compact them,
     * so for example "hello dolly" will become a single argument.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * String[] args = new String[3];
     * args[0] = "command";
     * args[1] = "\"hello";
     * args[2] = "dolly\"";
     *
     * // newArgs will be: ["command", "\"hello dolly\""]
     * String[] newArgs = Utils.compactQuotedArgs(args);
     * </pre>
     *
     * @param args A String[] array with all of the command's arguments.
     *
     * @return Returns a String[] array with quoted original arguments consolidated
     *         and the rest untouched.
     */
    @SuppressWarnings("NonConstantStringShouldBeStringBuffer")
    public static String[] compactQuotedArgs(final String[] args) {
        String tmpArg = "";
        boolean tmpArgDone = true;
        boolean beginningFound = false;
        boolean endingFound = false;
        List<String> newArgs = new ArrayList<String>();

        for (String arg : args) {
            // if the argument doesn't contain spaces but does contain quotes, simply unquote it
            final boolean hasSpaces = arg.contains(" ");
            final boolean startsWithQuote = arg.startsWith("\"");
            final boolean endsWithQuote = arg.endsWith("\"");

            if (!hasSpaces && startsWithQuote && endsWithQuote && !"\"".equals( arg )) {
                newArgs.add(arg.replace("\"", ""));
            }

            if (hasSpaces) {
                arg = arg.substring(0, arg.indexOf(' '));
            }

            if (startsWithQuote && tmpArgDone ) {
                tmpArg = arg.substring(1);
                tmpArgDone = false;
                beginningFound = true;
            } else if (endsWithQuote) {
                if ( !"\"".equals( arg ) ) {
                    tmpArg += ' ' + arg.substring(0, arg.length() - 1);
                }
                newArgs.add(tmpArg);
                tmpArgDone = true;
                endingFound = true;
            } else {
                if (!tmpArgDone) {
                    tmpArg += ' ' + arg;
                } else {
                    // tmp argument is done and this one does not contain quotes,
                    // so we're starting a new argument
                    newArgs.add(arg);
                }
            }
        }

        // if we've not found a closing bracket, add one ourselves
        if ( beginningFound && !endingFound ) {
            newArgs.add( tmpArg );
        }

        // if we've not found an opening bracket but we did find a closing one,
        // make everything except the first argument a redirect
        if ( !beginningFound && endingFound ) {
            final List<String> tmpArgs = new ArrayList<String>();
            boolean firstArgDone = false;
            String firstArg = "";

            for (String arg : newArgs) {
                if ( !firstArgDone ) {
                    firstArgDone = true;
                    firstArg = arg;
                    continue;
                }

                tmpArgs.add( arg );
            }

            newArgs.clear();
            newArgs.add( firstArg );
            newArgs.add( implode( tmpArgs, " " ) );
        }

        return newArgs.toArray(new String[newArgs.size()]);
    } // end method

    /***
     * Logs a debug message into the console if debug is enabled in AdminAnything.
     *
     * @param msg The message to log.
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything AdminAnything}.
     */
    public static void logDebug(final String msg, final AdminAnything aa) {
        if (aa.getDebug()) {
            //noinspection UseOfSystemOutOrSystemErr
            System.out.println(msg);
        }
    } // end method

    /**
     * Checks whether the given string has a matching set of parenthesis (for our purposes, brackets only).
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * // as used in the Permissions class
     * String query = "(I open but I won't close";
     *
     * if (!Utils.CheckParentesis(query)) {
     *   Bukkit.getLogger().severe("[AdminAnything] Invalid permissions query check passed to the checkPerms() method: " + query);
     *   return false;
     * }
     * }
     * </pre>
     *
     * @param str The actual query string to count matching brackets for.
     *
     * @return Returns true if the given query string has a matching number of brackets, false otherwise.
     */
    @SuppressWarnings({"ConstantConditions", "PointlessBooleanExpression"})
    public static boolean CheckParentesis(final String str) {
        // empty string will always have matching parenthesis count :P
        if (str.isEmpty()) {
            return true;
        }

        // create a new stack to store opening and closing parenthesis found
        final Stack<Character> stack = new Stack<Character>();

        // iterate over all characters
        for (int i = 0; i < str.length(); i++) {
            final char current = str.charAt(i);
            //if (current == '{' || current == '(' || current == '[')
            if ('(' == current) {
                // store opening parenthesis
                stack.push('(');
            }


            //if (current == '}' || current == ')' || current == ']')
            if (')' == current) {
                // closing bracket found but none was opened
                if (stack.isEmpty()) {
                    return false;
                }

                final char last = stack.peek();
                //if (current == '}' && last == '{' || current == ')' && last == '(' || current == ']' && last == '[')
                if ((')' == ')') && ('(' == last)) {
                    // we found a correct matching parenthesis
                    stack.pop();
                } else {
                    // the parenthesis found is not a matching pair - bail out
                    // note: this is only used when multiple parenthesis types are checked
                    return false;
                }
            }
        }

        // if the stack is empty, we found all the opening and closing parenthesis and they match
        return stack.isEmpty();
    } // end method

    /**
     * Adds top navigation chat links for moving back/forward
     * when pagination is used in a command.
     *
     * @param sender The person who's running the command that has paginated results.
     * @param fromIndex An index from which we start to show our current set of messages.
     * @param topMessage The actual FancyMessage to add pagination links to.
     * @param cmd A reference to the command that was ran.
     * @param args A reference to arguments of the command that was ran.
     * @param intRegex Regex for checking whether a number is integer.
     * @param requestedPageOriginal Original page requested.
     * @param isLeft Whether to generate left (previous) navigation arrow in chat (true) or right (next) arrow (false).
     */
    public static void addChatTopNavigation(CommandSender sender, int fromIndex, FancyMessage topMessage,
                                            Command cmd, String[] args, String intRegex, int requestedPageOriginal, boolean isLeft) {
        if ((sender instanceof Player) && (0 < fromIndex)) {
            topMessage.then((isLeft ? AA_API.__("general.previous-character") + " " : " " + AA_API.__("general.next-character"))).color(ChatColor.AQUA);

            final String             newCmd  = '/' + cmd.getName() + ' ';
            final Collection<String> newArgs = new ArrayList<>();

            for (final String arg : args) {
                if (arg.matches(intRegex) || arg.contains(AA_API.__("general.page") + ':') || arg
                    .contains("pg:")) { //NON-NLS
                    continue;
                } else {
                    newArgs.add(arg);
                }
            }
            newArgs.add(String.valueOf(requestedPageOriginal - 1));

            topMessage
                .command(newCmd + String.join(" ", newArgs))
                .tooltip(
                    AA_API.__("chat.navigation-show-next-prev-page",
                        (isLeft ? AA_API.__("chat.navigation-previous") : AA_API.__("chat.navigation-next")),
                        AA_API.__("general.page")
                    )
                );
        }
    } // end method

    /**
     * Returns a list of possible completions for a command parameter
     * that should be one of the commands currently present on the server.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * // as used in the aa_actions class (from the tabcomplete package)
     * \@Override
     * public List&lt;String&gt; onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
     *     if (args.length &gt; 1) {
     *     return null;
     *     }
     *
     *    return Utils.getServerCommandCompletions(args[0], command, alias);
     * } // end method
     * }
     * </pre>
     *
     * @param param   The actual command parameter (argument) we're trying to tab-complete.
     * @param command The command we're trying to tab-complete this parameter for.
     * @param alias   The alias with which the command we're trying to tab-complete this parameter for was called.
     *
     * @return Returns a list of commands that are possible to tab-complete in place of the given parameter.
     */
    public static List<String> getServerCommandCompletions(String param, Command command, String alias) {
        List<String> completions = new ArrayList<String>();
        try {
            StringUtil.copyPartialMatches(param, AA_API.getServerCommands(), completions);
        } catch (AccessException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            // don't show anything unless we're debugging, since this is a non game-breaking error
            if (AA_API.getDebug()) {
                Bukkit.getLogger()
                      .warning(AA_API.__("error.tabcomplete-could-not-load-commands", command.getName(), alias));
                e.printStackTrace();
            }
        }

        Collections.sort(completions);
        return completions;
    } // end method

    /**
     * Returns a list of possible completions for a command parameter
     * that should be one of the values stored in one in AdminAnything's lists,
     * for example in the list of disabled commands, muted commands etc.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * // as used in the aa_delredirect class (from the tabcomplete package)
     * \@Override
     * public List&lt;String&gt; onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
     *     // no completion if we have too many arguments
     *     if (args.length > 1) {
     *         return null;
     *     }
     *
     *     return Utils.getListValueCompletions(args[0], "redirects", command, alias);
     * } // end method
     * }
     * </pre>
     *
     * @param param   The actual command parameter (argument) we're trying to tab-complete.
     * @param command The command we're trying to tab-complete this parameter for.
     * @param alias   The alias with which the command we're trying to tab-complete this parameter for was called.
     *
     * @return Returns a list of commands that are possible to tab-complete in place of the given parameter.
     */
    public static List<String> getListValueCompletions(String param, String listName, Command command,
                                                       String alias, String[] args) {
        List<String> completions = new ArrayList<String>();
        List<String> existingPerms;

        //noinspection HardCodedStringLiteral
        existingPerms = "virtualperms".equals(listName) ?
                        makeListMutable(AA_API.getCommandsConfigurationValues(listName).keySet()) :
                        makeListMutable(AA_API.getCommandsList(listName));

        // build list of all permissions we previously used on the command line
        existingPerms.removeAll(Arrays.asList(Arrays.copyOf(args, args.length - 1)));

        StringUtil.copyPartialMatches(param, existingPerms, completions);
        Collections.sort(completions);

        return completions;
    } // end method

    /**
     * Method to join array elements of type string
     * @author Hendrik Will, imwill.com
     * @param inputArray Array which contains strings
     * @param glueString String between each array element
     * @return String containing all array elements seperated by glue string
     */
    public static String implode(Object[] inputArray, String glueString) {
        String output = "";

        if (inputArray.length > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(inputArray[0]);

            for (int i=1; i<inputArray.length; i++) {
                sb.append(glueString);
                sb.append(inputArray[i]);
            }
            output = sb.toString();
        }
        return output;
    }

    /**
     * Method to join list elements of type string
     * @author Hendrik Will, imwill.com, updated by Zathrus_Writer
     * @param listInputArray List&lt;String&gt; which contains strings
     * @param glueString String between each array element
     * @return String containing all array elements seperated by glue string
     */
    public static String implode(List<?> listInputArray, String glueString) {
        Object[] inputArray = listInputArray.toArray();
        return implode(inputArray, glueString);
    }

    /**
     * Checks whether we're on 64-bit or 32-bit processor architecture.
     *
     * @return Returns true if we're on 64-bits, false otherwise.
     */
    public static boolean is64Bit() {
        final String osArch = System.getProperty("os.arch");
        return "amd64".equals(osArch) || "x86_64".equals(osArch);
    } // end method

    /**
     * Makes the given string capitalized with the first letter being uppercased.
     *
     * @param txt The actual text to have capitalized.
     *
     * @return Returns a capitalized string, for example return "Hello" from the string "hello".
     */
    public static String capitalize(String txt) {
        return txt.substring(0, 1).toUpperCase() + txt.substring(1);
    }

    /**
     * Translates HEX chat colors into real colors for MC.
     *
     * @param startTag The start tag for the HEX color string, such as &#
     * @param endTag The end tag for the HEX color string, such as #& (can be empty).
     * @param message The actual message to translate HEX colors
     *
     * @return Returns a message formatted with HEX codes supported by the server.
     */
    public static String translateHexColorCodes(String startTag, String endTag, String message)
    {
        final Pattern hexPattern = Pattern.compile(startTag + "([A-Fa-f0-9]{6})" + endTag);
        Matcher matcher = hexPattern.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);
        while (matcher.find())
        {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, COLOR_CHAR + "x"
                + COLOR_CHAR + group.charAt(0) + COLOR_CHAR + group.charAt(1)
                + COLOR_CHAR + group.charAt(2) + COLOR_CHAR + group.charAt(3)
                + COLOR_CHAR + group.charAt(4) + COLOR_CHAR + group.charAt(5)
            );
        }
        return matcher.appendTail(buffer).toString();
    } // end method

    /**
     * Translates all known chat color codes into their color representations.
     *
     * @param message The message to translate chat colors in.
     *
     * @return Returns a message with color codes that can be used to send
     *         the message directy to player.
     */
    public static String translate_chat_colors( String message ) {
        // translate HEX colors first
        message = translateHexColorCodes( "&#", "", message );

        // then translate good old & colors
        message = ChatColor.translateAlternateColorCodes( '&', message );

        return message;
    } // end method

} // end class
