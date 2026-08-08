package com.trenton.updater.api;

public interface UpdaterService {
   void checkForUpdates(boolean var1);

   boolean isUpdateAvailable();

   String getLatestVersion();

   void handleUpdateOnShutdown();
}
