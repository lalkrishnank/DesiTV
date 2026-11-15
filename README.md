# Desi TV – Android TV IPTV app

Plays the free channels from the iptv-org playlist (`https://iptv-org.github.io/iptv/index.m3u`),
filtered to **Malayalam, Tamil, Hindi and English**.

* **Malayalam first and default:** channels 101+ are Malayalam, then Tamil (1001+), Hindi (2001+) and English (3001+).
  The app opens straight on the first Malayalam channel.
* **Daily auto-sync:** a background job re-downloads the playlist every 24 hours, even if the app isn't opened.
  It also runs after a reboot. There's a **Sync now** button in the channel list.
* **Live Channels integration:** the app registers as a TV input ("Desi TV"), so all channels show up in
  Google's **Live Channels** app (and other TV-input-based apps) with numbers, names, logos and a guide entry.

## Build the APK (no Android Studio needed)

1. Create a new **private** repository on github.com and upload this folder (or `git push` it).
2. Open the repo's **Actions** tab. The **Build APK** workflow runs on every push, or you can start it with **Run workflow**.
3. When it finishes (~5 min), open the run and download **DesiTV-apk**. It's a zip containing `app-release.apk`.

Or open the folder in **Android Studio** and use **Build > Build APK(s)**.

## Install on the TV

* **Using a USB stick:** copy the APK onto it and open it with a file manager on the TV (e.g. *File Commander*).
* **Using the Downloader app:** upload the APK somewhere and enter the link in *Downloader*.
* **Using adb:** run `adb connect <tv-ip>` and then `adb install app-release.apk`.

You'll need to allow **Install unknown apps** for whichever app does the install (Settings > Device Preferences > Security).

## Connect to Live Channels

1. Install **Live Channels** (by Google) from the Play Store if it isn't already on the TV.
2. Open Live Channels > **Settings** (or the menu) > **Channel sources** > **Desi TV** and run setup.
   The setup screen downloads the channels and lists how many were added per language.
3. Channels appear as 101, 102… (Malayalam) and so on. Logos fill in over the next few minutes.
4. If new channels are added later and don't show, check Live Channels > Settings > **Customize channel list**.

## Remote control (in the Desi TV app)

| Key | Action |
|---|---|
| ▲ / ▼ or CH+ / CH− | Next / previous channel |
| OK, ◀ / ▶ or Menu | Show the channel list (language tabs + channels) |
| 0–9 | Type a channel number |
| Back | Close the list / exit |

## Settings in code

`app/src/main/java/com/syam/desitv/data/Config.kt`:

* `ENGLISH_INDIA_ONLY`: English has 2,000+ channels worldwide. Set it to `true` to keep only Indian English channels.
* `SYNC_INTERVAL_HOURS`: how often to sync (default 24).

Channel numbering and language order are set in `data/Language.kt`.

## Notes

* iptv-org lists publicly available streams. Some go offline or are geo-blocked from the UAE, so
  "not available" on a channel is normal. Press ▲/▼ to move on. The daily sync drops dead links as iptv-org removes them.
* The app downloads the language playlists from the same iptv-org repo
  (`/languages/mal.m3u`, `tam.m3u`, `hin.m3u`, `eng.m3u`). These hold the same channels as `index.m3u` already sorted by
  language, because `index.m3u` itself carries no language tag.
