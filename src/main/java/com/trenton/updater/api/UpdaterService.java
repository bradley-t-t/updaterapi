package com.trenton.updater.api;

/**
 * Checks a Spigot resource for new versions and can swap the plugin jar in
 * place across a restart.
 */
public interface UpdaterService {
   /**
    * Queries the latest published version and records whether it is newer
    * than the running one. When autoUpdate is true and an update exists,
    * also downloads the new jar so {@link #handleUpdateOnShutdown} can
    * install it.
    *
    * <p>Performs blocking network calls on the invoking thread.
    */
   void checkForUpdates(boolean autoUpdate);

   /**
    * Whether the last check found a newer version.
    */
   boolean isUpdateAvailable();

   /**
    * The version name found by the last check, or null before any check
    * completes.
    */
   String getLatestVersion();

   /**
    * Installs a previously downloaded update: deletes the plugin's old jars
    * from the plugins folder and moves the new jar in. Call from
    * {@code onDisable}; the new version loads on the next server start.
    */
   void handleUpdateOnShutdown();
}
