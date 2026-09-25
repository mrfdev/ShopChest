## Agent skills

### Issue tracker

Issues and specs are tracked in GitHub Issues for `mrfdev/ShopChest`. See `docs/agents/issue-tracker.md`.

### Domain docs

This repository uses a single-context domain-doc layout. See `docs/agents/domain.md`.

### Paper target and release checks

- Read `docs/agents/test-server.md` and `docs/release-readiness.md` before building or deploying.
- Maintain Paper 26.3 build 41 ALPHA and API `26.3.build.41-alpha`; consult the live
  PaperMC documentation index, official references, and exact-version Javadocs
  before changing the target. Experimental builds are intentional for this release.
- Build and test with JDK 25.0.4.1 using `JAVA_HOME` and `PATH`; retain Java 25
  toolchains and `--release 25`. Run the maintained local server with JDK 27.
- `gradle.properties` owns release metadata. Build 794 is reserved for
  `1.15.4-SNAPSHOT`; reuse it after failures. Keep the separate 26.2 archive
  branch outside the release branch ancestry so it consumes no release number.
- Preserve `servers/Paper-26.2`; only `servers/Paper-26.3` is maintained now.
  Keep both instances, generated artifacts, logs, and database files out of Git.
- The shared test server must stay stopped. Replace only manifest-identified
  ShopChest JARs after local tests, using a deployment lock and recoverable backups.
