# Contributing to Delvefold

Contributions, compatibility reports, translations, and focused bug fixes are welcome.

## Development setup

Use Java 21 and the checked-in Gradle wrapper:

```bash
./gradlew clean check build
./gradlew runClient
./gradlew runServer
```

`check` runs unit tests plus shipped-JSON and translation-key validation. Keep server-authoritative logic independent of client classes, preserve schema-2 compatibility, and never add silent chunk retrogen or loaded-dimension deletion.

## Pull requests

Keep each pull request scoped, explain player/server impact, and add tests for configuration, networking, filesystem, or lifecycle changes. Update README, command/configuration documentation, schemas, examples, and the changelog when behavior changes. Do not commit generated run worlds, logs, IDE files, credentials, or Gradle caches.

For translations, copy `src/main/resources/assets/delvefold/lang/en_us.json` to the appropriate locale and translate values without renaming keys or format placeholders. Test narrow window sizes and long translated labels when changing GUI text.

By contributing, you agree that your contribution is licensed under the repository's MIT License.
