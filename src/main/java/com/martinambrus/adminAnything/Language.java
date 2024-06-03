package com.martinambrus.adminAnything;

import org.bukkit.Bukkit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Language class, contains all functionality required
 * to display translated text content in AdminAnything.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("HardCodedStringLiteral")
final class Language {

    /**
     * Instance of {@link AdminAnything}.
     */
    final AdminAnything plugin;

    /**
     * A regular expression to remove all apostrophes from a string.
     * It's here, so Java doesn't have to compile this over and over
     * when we replace them in the translated string returned.
     */
    private final Pattern deApostrophize = Pattern.compile("''");

    /**
     * The .properties language file contents
     * from a language file chosen by the server's owner.
     */
    private final Properties translations = new Properties();

    /**
     * The language file from which we're loading our translations.
     */
    private File langFile;

    /**
     * Constructor, stores the instace of AdminAnything.
     *
     * @param aa The singleton instance of AdminAnything.
     */
    Language(final AdminAnything aa) {
        plugin = aa;
    } //end method

    /**
     * Initialization function. Will check for old translation files
     * still present, warn in console if they are and try to load the language file
     * provided in AA's config. If that file is not found, a default English file
     * will be loaded instead.
     *
     * @return Returns true if translations were loaded, false otherwise.
     */
    boolean init() {
        boolean         oldLangFilesFound      = false;
        File            oldLangFile            = null;
        ArrayList<File> additionalOldLangFiles = new ArrayList<File>();
        File            langFolder             = new File(AA_API.getAaDataDir() + "/languages");
        langFile = new File(AA_API.getAaDataDir() +
            "/languages/" +
            AA_API.getConfigString("lang") +
            '-' +
            AA_API.getAaVersion() +
            ".properties");

        // check for old language files and copy over changed lines from them if found
        if (langFolder.exists()) {
            for (File f : Objects.requireNonNull(langFolder.listFiles()) ) {
                String fname = f.getName();
                if (
                    !f.isDirectory() &&
                    fname.endsWith(".properties") &&
                    !fname.endsWith('-' + AA_API.getAaVersion() + ".properties")
                    ) {

                    oldLangFilesFound = true;

                    if (fname.startsWith( AA_API.getConfigString("lang") + '-' )) {
                        oldLangFile = f;
                    } else {
                        additionalOldLangFiles.add(f);
                    }
                }
            }
        }

        // store the actual selected resource file into datadir
        if (!langFile.exists()) {
            try {
                plugin.saveResource("languages/" + AA_API.getConfigString("lang") + ".properties", true);

                // rename the original file to one with a version string attached
                //noinspection ResultOfMethodCallIgnored
                new File(AA_API.getAaDataDir() + "/languages/" + AA_API.getConfigString("lang") + ".properties")
                    .renameTo(langFile);
            } catch (Throwable e) {
                // the language file selected don't seem to exist, let's load the English one instead
                langFile = new File(AA_API.getAaDataDir() + "/languages/en-gb-" + AA_API
                    .getAaVersion() + ".properties");

                // save it from resources if it's not present yet
                if (!langFile.exists()) {
                    plugin.saveResource("languages/en-gb.properties", true);

                    // rename the original file to one with a version string attached
                    //noinspection ResultOfMethodCallIgnored
                    new File(AA_API.getAaDataDir() + "/languages/en-gb.properties").renameTo(langFile);
                }

                plugin.getExternalConf().set("lang", "en-gb");
                plugin.getExternalConf().saveConfig();

                Bukkit.getLogger().severe(
                    AA_API.getAaName() + " the language file \"" +
                        AA_API.getConfigString("lang") + '-' + AA_API.getAaVersion() +
                        ".properties\" could not be loaded. Falling back to \"en-gb\".");
            }
        }

        // load translations
        try {
            FileInputStream fp = new FileInputStream( langFile );
            translations.load(
                new InputStreamReader( fp, StandardCharsets.UTF_8)
            );
            fp.close();
        } catch (IOException e) {
            Bukkit.getLogger().severe(AA_API
                .getAaName() + " was unable to load language file '" + langFile
                .getPath() + "' and had to be disabled!");
            e.printStackTrace();

            // disable AA - no translations, no text
            Bukkit.getPluginManager().disablePlugin(plugin);
            return false;
        }

        // copy over changed old language file lines into the new one
        if (oldLangFilesFound) {
            if ( oldLangFile != null ) {
                final Properties oldTranslation      = new Properties();
                boolean          translationsChanged = false;
                try {
                    FileInputStream fp = new FileInputStream(oldLangFile);
                    oldTranslation.load( new InputStreamReader( fp, StandardCharsets.UTF_8) );
                    fp.close();

                    // iterate over all translations and compare
                    for (String propertyName : oldTranslation.stringPropertyNames()) {
                        // the property in new language file can be null,
                        // since we might have removed some messages in the new version
                        if (translations.getProperty(propertyName) != null && !translations.getProperty(propertyName)
                                                                                           .equals(oldTranslation
                                                                                               .getProperty(propertyName))) {
                            if (AA_API.getDebug()) {
                                Bukkit.getLogger()
                                      .info("Updating translation property " + propertyName + " to: " + oldTranslation
                                          .getProperty(propertyName) + " (original = " + translations
                                          .getProperty(propertyName) + ")");
                            }

                            translationsChanged = true;
                            translations.setProperty(propertyName, oldTranslation.getProperty(propertyName));
                        }
                    }

                    // store updated translations in the translations file
                    if (translationsChanged) {
                        FileWriter writer = new FileWriter(langFile);
                        translations.store(writer, "Automatically Updated AA Language File");
                        writer.close();
                    }

                    // remove old language file
                    oldLangFile.delete();

                    Bukkit.getLogger().info(AA_API.getAaName() + " " + AA_API
                        .__("lang.old-lang-files-found", plugin.getDataFolder()
                                                               .getPath() + File.separatorChar + "languages"));
                } catch (IOException e) {
                    Bukkit.getLogger().info(AA_API.getAaName() + " " + AA_API.__("lang.old-lang-files-not-updated", plugin.getDataFolder().getPath() + File.separatorChar + "languages"));
                    e.printStackTrace();
                }
            }

            // check additional language files - if found, add new lines from current language file to them
            // and rename them to the new version
            if ( !additionalOldLangFiles.isEmpty() ) {
                for ( File oldAdditionalLangFile : additionalOldLangFiles ) {
                    final Properties oldTranslation      = new Properties();
                    boolean          translationsChanged = false;
                    try {
                        FileInputStream fp = new FileInputStream( oldAdditionalLangFile );
                        oldTranslation.load( new InputStreamReader( fp, StandardCharsets.UTF_8) );
                        fp.close();

                        // iterate over all translations in the old file and find lines that don't exist in the old file
                        for ( String propertyName : translations.stringPropertyNames() ) {
                            if ( oldTranslation.getProperty(propertyName) == null ) {
                                if (AA_API.getDebug()) {
                                    Bukkit.getLogger()
                                          .info( "Adding new translation property " + propertyName + " to old language file: " + oldAdditionalLangFile.getName() );
                                }

                                translationsChanged = true;
                                oldTranslation.setProperty( propertyName, translations.getProperty(propertyName) );
                            }
                        }

                        // store updated translations in the translations file
                        if (translationsChanged) {
                            FileWriter writer = new FileWriter(oldAdditionalLangFile);
                            oldTranslation.store(writer, "Automatically Updated AA Language File");
                            writer.close();

                            // rename old language file
                            File newLangFile = new File(AA_API.getAaDataDir(), "languages/" + oldAdditionalLangFile.getName().replaceAll("([^\\d]+)\\d\\.\\d\\.\\d\\.properties", "$1" + AA_API.getAaVersion() + ".properties"));

                            /*try {
                                Files.move(oldAdditionalLangFile.toPath(), newLangFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }*/
                            oldAdditionalLangFile.renameTo( newLangFile );
                            //oldAdditionalLangFile.delete();
                        }

                        Bukkit.getLogger().info(AA_API.getAaName() + " " + AA_API
                            .__("lang.old-lang-files-additional-file-found", oldAdditionalLangFile.getName()));
                    } catch (IOException e) {
                        Bukkit.getLogger()
                              .info(AA_API.getAaName() + " " + AA_API.__("lang.old-lang-files-additional-file-not-updated", oldAdditionalLangFile.getName()));
                        e.printStackTrace();
                    }
                }
            }
        }

        return true;
    } // end method

    /**
     * Returns translation for the given identifier, and optionally
     * a set of parameters. If this identifier is not found, the same
     * identifier is used instead of the translation and returned with
     * any given parameters replaced.
     *
     * @param identifier The actual identifier from the .properties file to work with.
     * @param params     Optional parameters used to format translations with placeholders.
     *
     * @return Returns a the requested translation with all optional placeholders correctly substituted.
     */
    String __(String identifier, Object... params) {
        String s = translations.getProperty(identifier);

        // if not found, just use what we were given
        if (null == s || s.isEmpty()) {
            if (AA_API.getDebug()) {
                //noinspection HardCodedStringLiteral
                Utils
                    .logDebug("The translation identifier " + identifier + " was not found in the translation file " + langFile
                        .getPath() + '!', plugin);
            }
            s = identifier;
        }

        // replace colors in parameters by real color codes
        if ( 0 < params.length ) {
            for ( int i = 0; i < params.length; i++ ) {
                if ( params[i] instanceof String ) {
                    params[i] = Utils.translate_chat_colors( (String) params[i] );
                }
            }
        }

        // check if we need to return formatted message
        return Utils.translate_chat_colors( this.deApostrophize
            .matcher(0 < params.length ? MessageFormat.format(s, params) : s)
            .replaceAll("'") );
    } // end method

} // end class