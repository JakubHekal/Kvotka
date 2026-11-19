# Kvotka

**Kvotka** gives you deep, transparent insight into how your phone consumes network data.

Built as a modern rewrite of the legacy [DataUsage](https://github.com/JakubHekal/DataUsage) app.

## ✨ Key Features

* **Per-SIM Plans:** Keep a separate monthly limit, reset day, and daily limit for each SIM. A plan created before any SIM is detected attaches itself to the first SIM Shizuku finds.
* **Per-SIM Usage (Shizuku):** Optional Shizuku integration reads per-SIM network statistics directly from the system. Switch SIMs on the Dashboard or filter the Apps list by SIM.
* **Custom & Smart Limits:** Set a monthly data cap and choose the day it resets. Use a fixed daily limit, or let Kvotka calculate a dynamic daily limit from your remaining allowance and usage history.
* **Usage History:** A daily chart shows your consumption over the billing period. Tap any day to inspect its used, remaining, and limit values.
* **Live Usage Notification:** A persistent notification shows your current daily usage, remaining data, and limit. The foreground service restarts after a reboot.
* **App Breakdown:** See exactly which applications consume mobile data, split into Received, Sent, and Total. Open any app for its icon, package name, and detailed totals.
* **Filtering & Sorting:** Filter by period (Today, This Month, Last Month, This Year, All Time) and by SIM. Sort by Total, Received, Sent, or App Name, ascending or descending.
* **System & Hidden Usage:** Includes System processes, Tethering and Hotspot, Other, and removed apps.
* **Localization:** English and Czech, with per-app language selection on Android 13 and newer.
* **Modern UI:** A clean Material Design 3 interface built with Jetpack Compose.

## 📱 Requirements

* Android 8.0 (API 26) or newer.
* [Shizuku](https://shizuku.rikka.app/) is optional. Install it and start it via root or ADB to enable per-SIM usage statistics and SIM-aware plans.

## 🔒 Permissions Explained

Kvotka processes all data on-device. Nothing is uploaded.

* `android.permission.PACKAGE_USAGE_STATS` **(Usage Access):** Reads the device's historical network traffic statistics. Grant it in `Settings > Apps with usage access` when prompted.
* `android.permission.POST_NOTIFICATIONS`: Shows the persistent usage notification and limit warnings.
* `android.permission.QUERY_ALL_PACKAGES`: Resolves names and icons for every app in the usage list.
* `android.permission.READ_PHONE_STATE`: Read only on Android 9 and older, to display SIM carrier names. Per-SIM statistics use Shizuku instead.
* `moe.shizuku.manager.permission.API_V23` **(optional):** Granted through the Shizuku app, used only to run `dumpsys netstats` for per-SIM data.

## 📥 Downloading the App

Download the latest stable `.apk` from the [Releases](https://github.com/JakubHekal/Kvotka/releases) page.
