# Release Readiness and Documentation Freeze

This repository contains the prepared documentation source for the next
ShopChest rollout. Prepared documentation is not deployment approval: do not
publish it as live behavior until the matching jar has passed the complete
Storefront beta checklist and has been deliberately installed.

## Sources of Truth

Use these files when implementation and prose disagree:

1. `gradle.properties` defines the plugin version, build number, Java target,
   Paper version, Paper build, channel, and exact Paper API coordinate.
2. `plugin/src/main/resources/config.yml` defines every shipped configuration
   key and default.
3. `plugin/src/main/resources/plugin.yml` defines declared permissions and
   their Bukkit defaults.
4. `ShopCommand`, `ShopTabCompleter`, and their handlers define executable
   command syntax and suggestions.
5. `docs/plugin-docs.yml` defines the files imported by the central
   documentation site.
6. The reviewed JSON and CSV under `docs/catalogue/` are a dated marketplace
   snapshot. They are data artifacts, not current-stock documentation.

`./gradlew clean check` enforces release metadata, complete configuration-key
coverage, declared-permission coverage, the canonical Storefront Profile
route, manifest file references, and local Markdown links. A failure is a
release blocker, not a warning to ignore.

## Freeze the Candidate

Complete these steps on one fixed commit:

1. Finish the [Storefront beta test](storefront-beta-test.md), including rapid
   trade and Storefront Display interaction checks with ordinary player
   permissions.
2. Review [commands](commands.md), [configuration](configuration.md),
   [permissions](permissions.md), [integrations](integrations.md), and
   [troubleshooting](troubleshooting.md) against that commit.
3. Run `./gradlew clean check` with Java 25, then build the shaded jar with
   `./gradlew :plugin:shadowJar`.
4. Record the commit, complete artifact filename, and SHA-256 checksum in the
   release record. Do not rebuild after recording the checksum.
5. Start a clean Paper 26.2 test server with exactly the candidate jar and the
   intended live dependencies. Stop it cleanly after the final checks.
6. Confirm that `/shops profile ` suggests profile actions and `shopowner`, and
   that player names appear only after `/shops profile shopowner `.
7. Confirm the selected `storefront-discovery.location-scope`. Test the actual
   WorldGuard region when using `MARKETPLACE`; explicitly approve coordinate
   disclosure when using `GLOBAL`.
8. Verify `/shops admin advertise currency status`. Recapture only if the
   authoritative AFK Shrine Token template is missing or intentionally changed.

If code, defaults, permissions, command syntax, release metadata, or imported
files change after this freeze, restart the checklist and record a new artifact
checksum.

## Controlled Go-Live Order

When a future maintenance window is approved:

1. Back up `plugins/ShopChest/` and the configured ShopChest database.
2. Stop the server cleanly. Never hot-swap or plugin-manager reload ShopChest.
3. Keep exactly one top-level ShopChest jar and install the frozen candidate.
4. Start the server and complete the short production verification in
   [Installation and Updates](installation.md).
5. Verify search, own and `shopowner` profiles, the clickable Storefront
   Display, advertising status, and one controlled trade in each direction.
6. Publish the matching documentation only after the installed artifact passes
   those checks. From the central `1MB-Plugins-Docs` checkout, sync only the
   frozen, clean ShopChest source commit, regenerate, validate, and build:

   ```text
   node scripts/sync-docs.mjs --project shopchest
   npm run docs:generate
   npm run docs:validate
   npm run build
   ```

   Review only the ShopChest namespace diff and confirm its `SYNCED_FROM.md`
   names the recorded source commit with a clean source state. Do not imply
   that an unreleased feature is already live.
7. Export and publish a new marketplace snapshot only when explicitly desired;
   review and replace its JSON and CSV together.

If production verification fails, restore the backed-up plugin directory and
database with the server stopped, reinstall the previous known-good jar, and
keep the prepared documentation unpublished until a new candidate passes.
