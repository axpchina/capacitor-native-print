# @robusthomes/capacitor-native-print

Capacitor plugin for opening the native print sheet from the current WebView page.

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

Install from local checkout:

```bash
npm install file:../capacitor-plugins/capacitor-native-print
npx cap sync
```

Install from npm:

```bash
npm install @robusthomes/capacitor-native-print
npx cap sync
```

## Recommended app-side wrapper

Keep app code behind a facade such as `src/lib/certificatePrint.ts`:

```ts
import { NativePrint } from '@robusthomes/capacitor-native-print';

export const openCertificatePrint = async (jobName: string) => {
  const availability = await NativePrint.isAvailable();
  if (!availability.available) {
    throw new Error(availability.reason || 'Printing is unavailable.');
  }

  return NativePrint.printCurrentPage({ jobName });
};
```

## Notes

- No additional iOS or Android runtime permissions are required.
- Change the npm scope before publishing if your registry uses a different organization or username.
- Source repository: `https://github.com/axpchina/capacitor-native-print`
