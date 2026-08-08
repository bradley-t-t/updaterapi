package com.trenton.updater.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@link UpdaterService} backed by the Spiget API.
 *
 * <p>Version checks hit {@code api.spiget.org} for the resource's latest
 * version name; downloads land in the plugin's {@code AutoUpdater} data
 * subfolder until {@link #handleUpdateOnShutdown} installs them. Versions
 * compare numerically segment by segment after stripping non-numeric
 * characters, so {@code v1.10} is newer than {@code 1.9}.
 */
public class UpdaterImpl implements UpdaterService {
   private final JavaPlugin plugin;
   private final int resourceId;
   private String latestVersion;
   private boolean updateAvailable;
   private File downloadedUpdate;

   public UpdaterImpl(JavaPlugin plugin, int resourceId) {
      this.plugin = plugin;
      this.resourceId = resourceId;
      this.updateAvailable = false;
   }

   public void checkForUpdates(boolean autoUpdate) {
      try {
         URL url = new URL("https://api.spiget.org/v2/resources/" + this.resourceId + "/versions/latest");
         HttpURLConnection connection = (HttpURLConnection) url.openConnection();
         connection.setRequestMethod("GET");
         connection.setRequestProperty("User-Agent", this.plugin.getName() + "-Updater");
         connection.setConnectTimeout(5000);
         connection.setReadTimeout(5000);
         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            this.plugin.getLogger().warning("Failed to check for updates: HTTP " + responseCode);
            return;
         }

         StringBuilder response = new StringBuilder();
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
               response.append(line);
            }
         }

         connection.disconnect();
         JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
         if (!json.has("name")) {
            this.plugin.getLogger().warning("Spiget API response missing 'name' field");
            return;
         }

         this.latestVersion = json.get("name").getAsString().trim();
         String currentVersion = this.plugin.getDescription().getVersion().trim();
         if (this.isVersionNewer(this.latestVersion, currentVersion)) {
            this.updateAvailable = true;
            this.plugin.getLogger().info("Update available: v" + this.latestVersion + " (current: v" + currentVersion + ")");
            if (autoUpdate) {
               this.downloadUpdate();
            }
         } else {
            this.plugin.getLogger().info("No update available. Current: v" + currentVersion + ", Spigot: v" + this.latestVersion);
         }
      } catch (Exception e) {
         this.plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
      }
   }

   private boolean isVersionNewer(String latest, String current) {
      try {
         String normalizedLatest = latest.replaceAll("[^0-9.]", "").trim();
         String normalizedCurrent = current.replaceAll("[^0-9.]", "").trim();
         String[] latestParts = normalizedLatest.split("\\.");
         String[] currentParts = normalizedCurrent.split("\\.");
         int maxLength = Math.max(latestParts.length, currentParts.length);

         for (int i = 0; i < maxLength; ++i) {
            int latestNum = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
            int currentNum = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            if (latestNum > currentNum) {
               return true;
            }

            if (latestNum < currentNum) {
               return false;
            }
         }

         return false;
      } catch (NumberFormatException e) {
         this.plugin.getLogger().warning("Invalid version format: latest=" + latest + ", current=" + current);
         return false;
      }
   }

   private void downloadUpdate() {
      try {
         File updateFolder = new File(this.plugin.getDataFolder(), "AutoUpdater");
         if (!updateFolder.exists()) {
            updateFolder.mkdirs();
         }

         this.downloadedUpdate = new File(updateFolder, this.plugin.getName() + "-" + this.latestVersion + ".jar");
         if (this.downloadedUpdate.exists()) {
            this.plugin.getLogger().info("Update file " + this.downloadedUpdate.getPath() + " already exists.");
            return;
         }

         URL url = new URL("https://api.spiget.org/v2/resources/" + this.resourceId + "/download");
         HttpURLConnection connection = (HttpURLConnection) url.openConnection();
         connection.setRequestProperty("User-Agent", this.plugin.getName() + "-Updater");
         connection.setConnectTimeout(5000);
         connection.setReadTimeout(5000);
         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            this.plugin.getLogger().warning("Failed to download update: HTTP " + responseCode);
            return;
         }

         try (InputStream inputStream = connection.getInputStream();
              ReadableByteChannel channel = Channels.newChannel(inputStream);
              FileOutputStream out = new FileOutputStream(this.downloadedUpdate)) {
            out.getChannel().transferFrom(channel, 0L, Long.MAX_VALUE);
            this.plugin.getLogger().info("Downloaded update to " + this.downloadedUpdate.getPath() + ".");
         }

         connection.disconnect();
      } catch (Exception e) {
         this.plugin.getLogger().warning("Failed to download update: " + e.getMessage());
         this.downloadedUpdate = null;
      }
   }

   public void handleUpdateOnShutdown() {
      if (this.downloadedUpdate != null && this.downloadedUpdate.exists()) {
         try {
            File pluginsFolder = new File("plugins");
            File currentJar = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            File[] existingJars = pluginsFolder.listFiles((dir, name) -> name.startsWith(this.plugin.getName()) && name.endsWith(".jar"));
            if (existingJars != null) {
               for (File jar : existingJars) {
                  try {
                     Files.deleteIfExists(jar.toPath());
                     this.plugin.getLogger().info("Deleted old JAR: " + jar.getPath());
                  } catch (Exception e) {
                     this.plugin.getLogger().warning("Failed to delete old JAR " + jar.getPath() + ": " + e.getMessage());
                  }
               }
            }

            if (currentJar.exists() && currentJar.isFile()) {
               try {
                  Files.deleteIfExists(currentJar.toPath());
                  this.plugin.getLogger().info("Deleted current JAR: " + currentJar.getPath());
               } catch (Exception e) {
                  this.plugin.getLogger().warning("Failed to delete current JAR " + currentJar.getPath() + ": " + e.getMessage());
               }
            }

            File targetFile = new File(pluginsFolder, this.plugin.getName() + "-" + this.latestVersion + ".jar");
            Files.move(this.downloadedUpdate.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            this.plugin.getLogger().info("Moved update to " + targetFile.getPath() + ". Restart server to apply.");
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to apply update: " + e.getMessage());
            this.plugin.getLogger().info("To apply update manually: 1) Stop server. 2) Remove old " + this.plugin.getName() + " JARs. 3) Move " + this.downloadedUpdate.getPath() + " to plugins/" + this.plugin.getName() + "-" + this.latestVersion + ".jar. 4) Restart server.");
         }
      }
   }

   public boolean isUpdateAvailable() {
      return this.updateAvailable;
   }

   public String getLatestVersion() {
      return this.latestVersion;
   }
}
