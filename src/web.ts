import { WebPlugin } from '@capacitor/core';

import type {
  NativePrintAvailabilityResult,
  NativePrintPlugin,
  PrintCurrentPageOptions,
  PrintCurrentPageResult,
} from './definitions';

type PluginError = Error & { code?: string };

const createPluginError = (code: string, message: string): PluginError => {
  const error = new Error(message) as PluginError;
  error.code = code;
  return error;
};

export class NativePrintWeb extends WebPlugin implements NativePrintPlugin {
  async isAvailable(): Promise<NativePrintAvailabilityResult> {
    const available = typeof window !== 'undefined' && typeof window.print === 'function';

    return {
      available,
      platform: 'web',
      reason: available ? undefined : 'window.print is not available in this environment.',
    };
  }

  async printCurrentPage(_options?: PrintCurrentPageOptions): Promise<PrintCurrentPageResult> {
    const availability = await this.isAvailable();

    if (!availability.available) {
      throw createPluginError('UNAVAILABLE', availability.reason || 'Printing is unavailable.');
    }

    try {
      window.print();
      return {
        started: true,
        platform: 'web',
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to open the browser print dialog.';
      throw createPluginError('PRINT_FAILED', message);
    }
  }
}
