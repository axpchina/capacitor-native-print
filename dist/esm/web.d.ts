import { WebPlugin } from '@capacitor/core';
import type { NativePrintAvailabilityResult, NativePrintPlugin, PrintCurrentPageOptions, PrintCurrentPageResult } from './definitions';
export declare class NativePrintWeb extends WebPlugin implements NativePrintPlugin {
    isAvailable(): Promise<NativePrintAvailabilityResult>;
    printCurrentPage(_options?: PrintCurrentPageOptions): Promise<PrintCurrentPageResult>;
}
//# sourceMappingURL=web.d.ts.map