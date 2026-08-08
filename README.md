# UpdaterAPI

Update checker and auto-updater for Spigot plugins, backed by the Spiget
API. It compares the running version against the resource's latest published
version, optionally downloads the new jar, and swaps it into the plugins
folder at shutdown so the next restart runs the new version.

## Usage

```java
public class MyPlugin extends JavaPlugin {
    private UpdaterService updater;

    @Override
    public void onEnable() {
        updater = new UpdaterImpl(this, RESOURCE_ID);
        updater.checkForUpdates(true); // true also downloads the update
    }

    @Override
    public void onDisable() {
        updater.handleUpdateOnShutdown();
    }
}
```

`RESOURCE_ID` is the numeric id in the resource's spigotmc.org URL.
`isUpdateAvailable()` and `getLatestVersion()` report the result of the last
check, for things like notifying admins on join.

Version names compare numerically segment by segment after non-numeric
characters are stripped, so `v1.10` counts as newer than `1.9`.
`checkForUpdates` performs blocking network calls on the thread that invokes
it.

## Requirements

- Spigot/Paper API 1.21+
- Java 17+

## Installing to your local Maven repository

```bash
mvn install
```

Then depend on it:

```xml
<dependency>
    <groupId>com.trenton</groupId>
    <artifactId>UpdaterAPI</artifactId>
    <version>1.0.0</version>
</dependency>
```
