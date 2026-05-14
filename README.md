# Kvotka

**Kvotka** is designed to give you deep, transparent insights into how your phone consumes network data. 

Built from the as a modern rewrite of the legacy [DataUsage](https://github.com/JakubHekal/DataUsage) app.

## ✨ Key Features

* **Custom & Smart Data Limits:** Set personalized daily and monthly data caps. Kvotka can even automatically calculate an optimal daily limit for you based on your current usage and remaining monthly allowance.
* **Live Usage Notification:** Stay updated at a glance with a persistent notification displaying your real-time daily data consumption overview.
* **App Breakdown:** Track exactly which applications are consuming your mobile data, categorized by Bytes Sent, Received, and Total.
* **Filtering & Sorting:** Sort usage alphabetically, by highest consumption, or by specific timeframes (Today, This Month, All Time).
* **System & Hidden Usage:** Can also show data usage of System processes, Wi-Fi Tethering/Hotspots, and Uninstalled apps.
* **Modern UI:** A clean Material Design 3 UI.

## 🔒 Permissions Explained

To provide accurate data tracking, Kvotka strictly requires specific Android system permissions. The app handles these securely and entirely on-device.

* `android.permission.PACKAGE_USAGE_STATS` **(Usage Access):** Required to read the device's historical network traffic statistics. The app prompts the user to grant this via `Settings > Apps with usage access` on first launch.
* `android.permission.POST_NOTIFICATIONS`: Required to display foreground service alerts and data warnings.

## 📥 Downloading the App
You can download the latest stable `.apk` directly from the [Releases](https://github.com/JakubHekal/Kvotka/releases) page.
