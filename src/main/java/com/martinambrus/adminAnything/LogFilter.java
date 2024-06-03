package com.martinambrus.adminAnything;

import com.martinambrus.adminAnything.commands.Aa_mutecommand;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * Alternative, semi-functional commands muting class for instances
 * when we can't alter original server classes in-memory
 * (i.e. we can't instrument them).<br><br>
 *
 * This class only ever gets utilized if:<br>
 * 1. we can't instrument server classes<br>
 * 2. we are indeed muting some commands
 *
 * @author Martin Ambrus
 */
public final class LogFilter implements Filter {

    /***
     * Checks the log record to make sure we're filtering
     * messages via AdminAnything, then mutes the record
     * if it's within a certain time threshold.
     */
    @Override
    public boolean isLoggable(final LogRecord record) {
        // disable logging for up to 1.5 seconds after a muted command was executed
        // to prevent broadcastMessage() messages in console
        return "com.martinambrus.adminAnything".equals(record //NON-NLS
                                                              .getLoggerName()) || (0 >= Aa_mutecommand.lastMuteTimestamp) || (!((Aa_mutecommand.lastMuteTimestamp + 1.5) > Utils
            .getUnixTimestamp()));

    } //end method

} // end class