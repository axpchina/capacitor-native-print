# NativePrint 0.2.0 release notes

This release adds printing of an existing app-private PDF while retaining the
current-page API. See the README for input roots, result semantics and errors.
The package is a local release candidate until its final evidence is qualified
and the Human separately authorizes npm publication.

## Contract and user experience

Click Print opens the platform print UI. Android resolves `handed_off` after
system handoff; iOS resolves when its interaction completes or is cancelled.
Neither platform result proves paper output. No new service, notification,
permission, remote PDF processor or account access is introduced.

The plugin makes an immutable private snapshot. Applications retain ownership
of their source file. Android cleanup occurs at adapter finish, which can be
later than Promise completion. The existing per-instance PDF guard returns
`BUSY`; callers must serialize mixed legacy/PDF API requests as documented.

## Deferred limitation

`ANDROID_17_LONG_DWELL_PRINT_DELAY` remains P2 / KNOWN_ISSUE / DEFERRED for the
observed Android 17 Pixel 8 Pro synthetic QA scenario after roughly 90 seconds
in system Print UI. Read [the exact scope and recovery limitation](KNOWN_ISSUES.md).
This is explicit risk acceptance, not a fix or an Android-wide reliability claim.

## Compatibility and integration boundary

The npm peer contract is Capacitor 8.x; minimum native targets remain Android
API 24 and iOS 15. Validation is specific to the recorded hosts and devices;
minimum-target support is not a claim of device testing every OS version.

The isolated QA host was aligned from Android 8.3.4 to 8.5.2 while Core/iOS/CLI
were 8.5.2. That alignment did not fix long-dwell behavior. It must not be used
alone as evidence for a Product host still on Android 8.3.4. Product dependency
alignment/integration requires a separate task and fresh Product verification;
this plugin release does not update Product dependencies or clear its P0 blocker.

The accepted iOS implementation is preserved, with historical 44-photo,
66-photo and Long Note device evidence. This release preparation performs no
new physical iPhone validation.

## Dependency and security maintenance

The Android engine is a private source patch of PDFBox Android 2.0.27.0, with
pinned upstream inputs and archive hashes. It is not a claim that this historical
upstream version is currently maintained. Its source and assets retain their
licenses and notices. See [engine documentation](android/vendor/README.md).

Strict validation rejects malformed/repair-requiring and encrypted inputs.
All parser output sinks are removed at source, and safe fixed exceptions are
used at plugin boundaries. The private patch and its transitive dependency
reachability require continued maintenance and regression review. A successful
16-case corpus is not proof against all possible malformed documents.

## Human publication checklist

- Confirm the final commit/tree and exact local tarball SHA-256 from the release
  evidence; confirm all mandatory gates passed on that candidate.
- Confirm 0.2.0 is still available in the intended npm scope and confirm package
  contents, licenses, API declarations and the deferred issue remain accurate.
- Authorize publication of that exact tarball separately. Do not substitute a
  newly packed artifact without rechecking its identity.
- Git push/tagging and RobustHomes Product integration/release remain separate
  decisions. No command in this preparation publishes or changes npm dist-tags.

See [SECURITY_REVIEW.md](SECURITY_REVIEW.md) for the scoped dependency review and its limits.
