# Changelog

## 0.2.0

- Add native `printPdf({ fileUri, jobName? })` for existing app-private PDFs.
- Preserve `isAvailable()` and `printCurrentPage()` contracts and the legacy Web
  current-page fallback; Web PDF printing rejects with `UNAVAILABLE`.
- Use private snapshots and native PDF printing. Android preserves page content
  and supports source-order selected-page extraction without rasterizing pages.
- Reject invalid, unreadable, encrypted and malformed inputs with fixed errors.
  The privately namespaced PDF engine removes document-derived diagnostic output.
- Remove temporary Android lifecycle diagnostics from the runtime candidate.
- Include the private engine source, manifest, provenance and license notices in
  the npm distribution so Android consumers can build the package.
- Declare Capacitor 8.x compatibility; no future Capacitor major is implied.
- Document `ANDROID_17_LONG_DWELL_PRINT_DELAY` as P2 / KNOWN_ISSUE / DEFERRED under
  explicit Human acceptance. It is not fixed and is not counted as a test PASS.

## 0.1.0

- Existing native current-page printing and Web fallback baseline.
