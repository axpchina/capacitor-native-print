# @robusthomes/capacitor-native-print

Capacitor 8 plugin for native current-page and app-private PDF printing.

Repository: [https://github.com/axpchina/capacitor-native-print](https://github.com/axpchina/capacitor-native-print)

## What it does

- iOS: opens `UIPrintInteractionController`
- Android: opens `PrintManager`
- Web: falls back to `window.print()`

## API

```ts
import { NativePrint } from '@robusthomes/capacitor-native-print';

await NativePrint.isAvailable();
await NativePrint.printCurrentPage({ jobName: 'Certificate-123' });
```

### `isAvailable()`

Returns:

```ts
{
  available: boolean;
  platform: 'ios' | 'android' | 'web';
  reason?: string;
}
```

### `printCurrentPage(options?)`

Options:

```ts
{
  jobName?: string;
}
```

Returns:

```ts
{
  started: boolean;
  platform: 'ios' | 'android' | 'web';
}
```

## Install

Install from npm:

```bash
npm install @robusthomes/capacitor-native-print
npx cap sync
```

## Recommended app-side wrapper

Keep app code behind a facade such as `src/lib/nativePrint.ts`:

```ts
import { NativePrint } from '@robusthomes/capacitor-native-print';

export const printCurrentPage = async (jobName: string) => {
  const availability = await NativePrint.isAvailable();
  if (!availability.available) {
    throw new Error(availability.reason || 'Printing is unavailable.');
  }

  return NativePrint.printCurrentPage({ jobName });
};
```

## Notes

- No additional iOS or Android runtime permissions are required.
- Published package: `@robusthomes/capacitor-native-print`
- Source repository: `https://github.com/axpchina/capacitor-native-print`

## Direct PDF printing

```ts
await NativePrint.printPdf({ fileUri: savedPdfUri, jobName: 'Report' });
```

`fileUri` must be an existing, readable, unencrypted PDF with at least one page.
Only `file:` URLs inside the application's canonical private directories are accepted:
iOS Documents, Caches and temporary storage; Android files/cache and app-specific
external files/cache. Remote URLs, `content:` URLs and paths outside those roots are
rejected. No PDF Base64 crosses this API. Generate and save the complete PDF before
calling; use that same document for Download PDF.

- iOS supplies a private PDF snapshot URL to UIKit `printingItem`, preserving PDF
  pages. The promise settles when UIKit completes the interaction: `cancelled` or
  `interaction_completed`. Saving/sharing from the print UI may count as completion.
- Android supplies a PDF adapter to `PrintManager`. `handed_off` means the system
  accepted the adapter, not that the user submitted or completed a print job.
  Subsequent cancellation or spooler failures are not promise results.
- Every result includes `platform: 'ios' | 'android'`. No result proves paper output.
- Web rejects `printPdf` with `UNAVAILABLE`; it never prints the current HTML page
  as a substitute. The existing `printCurrentPage` Web fallback stays unchanged.
- `isAvailable` retains its original current-page meaning; it is not PDF validation.

PDF requests are serialized per plugin instance (`BUSY`). Applications must also
serialize old and new API calls; the legacy API has no concurrency guard.
Errors use `INVALID_URI`, `FILE_UNREADABLE`, `INVALID_PDF`, `UNAVAILABLE`, `BUSY` or
`PRINT_FAILED`. Native errors deliberately omit source paths and PDF contents.

Android compiles a pinned, privately namespaced and security-patched PDFBox Android
2.0.27.0 source archive for PDF validation and vector page extraction. The original
upstream AAR is not loaded. See [engine provenance, licenses and maintenance](android/vendor/README.md). All-page writes stream the snapshot; selected pages are copied
in source order with resources, page boxes and rotation, without rasterization.
Disk scratch storage avoids multiple in-memory PDF copies. No broad storage
permission is added. The declared compatibility range is Capacitor 8.x, iOS 15+, Android API 24+.
Capacitor 9.x and future major versions are not claimed by this release.

A private snapshot isolates printing from later source changes. Keep the source
until the promise has settled; the plugin does not delete the caller's file.
Snapshots and Android parser scratch files are removed at normal interaction finish
or preparation failure. Android retains its snapshot until adapter `onFinish`,
which can be later than promise resolution. Process termination may leave cache
files for the OS to evict; no guaranteed crash-time deletion is claimed. System
print services control their own spool copies. Avoid confidential job names.

## Verification

`npm run build`, `npm run verify:web`, and `node --test tests/web.test.cjs` validate
the TypeScript/Web surface. The Android instrumentation test lives under
`android/src/androidTest`; execute it only in an isolated synthetic QA host.
Physical-device PDF content and cancellation/selection tests are separate gates.
No release or npm publication is implied by a successful build.

## Release limitations

Read [known issues](KNOWN_ISSUES.md), [release notes](RELEASE_NOTES.md), and the
[changelog](CHANGELOG.md) before integrating. Android `handed_off` is not a
print-success notification. Long system Print UI dwell on the observed Android 17
device can delay completion; the issue is deferred, not fixed. No foreground
service, notification channel or additional permission is introduced.

The plugin source is MIT licensed. The private PDF engine and bundled resources
retain their upstream license and notices under `android/vendor/`.
