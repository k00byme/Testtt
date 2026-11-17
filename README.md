# Status Colors (Aliucord)

Aliucord plugin to customize presence indicator colors independently:

- Online
- Idle
- Do Not Disturb
- Offline / Invisible

Colors are entered as `#RRGGBB` and saved automatically through Aliucord's plugin Settings API (`/Aliucord/settings/StatusColors.json`).

## Build

This project follows the official Aliucord plugin template and targets Discord `126021` (126.21). It requires JDK 21, Android SDK 36, and Gradle 9.3.1.

From the repository root, run `gradle make` (or open the project in Android Studio and run the `make` Gradle task). The included GitHub Actions workflow also builds the plugin automatically on pushes to `main`.

The plugin ZIP is generated under:

```text
plugins/StatusColors/build/outputs/StatusColors.zip
```

## Scope

The plugin recolors status indicators locally in the current Aliucord installation. It does not change a Discord user's actual presence or affect what other Discord clients see.

The initial implementation hooks `UserAvatarPresenceView` and `StatusView`, so runtime verification on the target Aliucord build is still needed to confirm the current Discord view implementation exposes the presence dot through the same drawable/paint paths.
