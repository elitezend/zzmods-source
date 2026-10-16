# Z&Z Mods MC - mod source code

The code of **Auction Overlay** and **Gamble Tracker**, the Fabric mods from [zzmods.com](https://zzmods.netlify.app),
published so you can see exactly what they do before you install them.

This is the code of **Auction Overlay 2.1.0** and **Gamble Tracker 2.1.0**.

This is **source-available, not open source**: you can read it, but you can't copy, build or share it
(see [LICENSE](LICENSE)). Get the mods from zzmods.com.

| Folder | What it is |
| --- | --- |
| `auction-overlay/src` | Auction Overlay (Java, written for Minecraft 26.2) |
| `gamble-tracker/src` | Gamble Tracker |
| `*/resources` | the mod's metadata, mixin list, icon and font |
| `*/minecraft-1.21.11-changes` | the few files that are different in the Minecraft 1.21.11 version |

Free, Basic and Premium are the same code; the edition only switches features on or off.

## What the mods send to zzmods.com

Everything goes to `zzmods.netlify.app` over HTTPS. The code for it is in `Online.java` / `GambleOnline.java`
(update checks, stats, account link), `Updater.java` (one-click updates) and `Announcements.java`.

| When | What is sent | Why | Turn it off |
| --- | --- | --- | --- |
| When you join a world, then every 5 minutes | the mod name and its version number | to tell you about updates, and switch off a version with a serious bug until you update ("required update") | Update checks can be hidden in the mod's About settings; the required-update check always runs and sends only the version |
| Every 10 minutes while you play | a random install ID (not linked to you), mod, version, edition, Minecraft version, look, and usage counts (minutes played, sessions, auctions run / sold / cancelled, bids, payments counted, players queued, winners paid) | to see which versions and features people use | "Share stats" in About |
| Every 30 seconds, **only if you linked the mod to your zzmods account** | the link code, version and edition; with "Share stats" on also your Minecraft name and the same usage counts | to check your license (paid editions only work for the account that bought them) and to receive messages or test payments our support team sends you | Unlink in the mod's About tab or on your account page |
| Only when you click **Update now** | the link code, mod, edition and Minecraft version | to download the update; the jar is checked against our signing key (`UpdateKey.java`) before it's installed | just don't click it |
| Only if you paste your own Discord webhook (Auction Premium) | auction start / bids / sold messages, to **your** webhook | the Discord posts feature | remove the webhook |

The mods **never** send your chat, the servers you play on, your coordinates, payment amounts or anything from
your Minecraft account besides your in-game name (and that only when linked, with stats on).
The only command a mod ever sends in game is Gamble Tracker's pay-winner key, and only when you press it.

## Questions

Ask on our Discord: https://discord.gg/B64z86e9ZY
