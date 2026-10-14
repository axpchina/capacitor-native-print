# Private Android PDF engine

This is a reproducible, source-level security patch of
`com.tom-roush:pdfbox-android:2.0.27.0`, not an upstream release. The plugin compiles
`pdfbox-private-2.0.27.0-r1.zip` as private Java source and assets. It does **not**
resolve or package the original PDFBox Android AAR.

## Origin and license

The Maven Central source archive and official `v2.0.27.0` GitHub tag contain
identical Java files. `upstream-lock.json` pins those inputs and the original
AAR by SHA-256. The AAR supplies the unchanged asset bytes. `LICENSE.txt` and
`NOTICE.txt` retain the full upstream license and notices, including bundled
font/CMap attribution. Every relocated Java file carries a modification notice.
License/NOTICE copies are also installed as Android assets. Plugin code remains
MIT; the port and its resources retain their upstream licenses.

## Security correction

The upstream `COSParser.parseXrefTable()` calls `android.util.Log.w()` with a
line read from the PDF. It does this before returning failure, independently
of debug configuration and strict/lenient mode. Other parser and font paths
also log document-derived values. Catching parser exceptions cannot retract
these writes.

The generator removes every direct Android log, stdout/stderr and stack-trace
output statement in the pinned source. It removes their argument expressions,
so document content is never constructed for a diagnostic call. Warning/error
sites set only a thread-local boolean. The adapter resets that flag before an
operation, rejects flagged input/output with a fixed error, and clears the flag
in `finally`. Debug/info sites have no replacement output. No global Android
logging setting, consumer logging rule, Logcat filter or exception suppression
is used as the security control.

The entire `com.tom_roush` namespace and resource paths are relocated under
`com.robusthomes.capacitor.nativeprint.internal.pdfboxport`. A host's unrelated
PDFBox dependency/configuration is neither changed nor used. This engine is an
internal implementation detail; applications must not depend on its classes.

Encrypted documents are rejected before encryption dictionary processing or
crypto provider invocation, consistent with `printPdf`'s unencrypted-only
contract. The unused optional JPEG2000 codec remains unsupported; its direct
compile-time references are replaced by fixed errors. Ordinary JPEG/PNG report
images remain embedded, not decoded/recompressed during page extraction.

The adapter uses strict parsing, validates page metadata and content streams,
and never sends dependency exception text or causes across its public boundary.
Inputs requiring parser repair may now be rejected instead of being silently
recovered. No damaged PDF is deliberately printed to bypass validation.

## Printing and compatibility

The page-copying engine, disk scratch strategy, all-page streaming and selected
page import remain based on the same upstream implementation. Vector/text
content, image streams, page resources, boxes and rotation are preserved; no
whole-document rasterization or WebView HTML pagination is introduced. The
private source archive increases source distribution size, not the engine's
functional scope. Android API 24 and the existing Capacitor 8 contract remain.

The three existing Bouncy Castle 1.72 compile/runtime dependencies are declared
explicitly after removing the parent AAR. No additional crypto feature is
exposed. The private parser rejects encryption before it can reach those
providers. AndroidX and Capacitor versions are unchanged. See the release's
security/dependency review for advisory reachability and residual risks.

Android `PdfRenderer` cannot replace this engine's vector-preserving selected
page extraction across API 24+; rasterizing through `PdfDocument` would lose
searchable text and output fidelity. A new network PDF service is unnecessary.

## Reproduction and maintenance

Run `tools/prepare-private-pdfbox.py --source <source.jar> --tag <tag.zip>
--aar <original.aar>` from a verified checkout. It performs no network access,
requires the exact pinned input hashes, compares every Java file with the
release tag, preserves notices and assets, and emits a deterministic archive,
`security-patch.diff` (before namespace relocation), and a per-file manifest.
The Gradle build checks the archive hash before compiling it.

This local patch carries an ongoing plugin-maintainer obligation. Any upstream
version, transform, namespace, resource or diagnostic change must be reviewed;
reproduce the archive, compare the patch, review upstream/security advisories,
scan source and compiled classes for output sinks, and repeat malformed-input,
normal printing, selected-page, cancellation and final packed-consumer QA.
Never swap in an unpatched AAR as an automatic build fallback. The historical
version is pinned rather than represented as a currently maintained upstream
release. A later dependency migration is a separate reviewed change.

The archive contains no QA fixtures. Synthetic tests live in `src/androidTest`
and are excluded from the npm runtime package.
