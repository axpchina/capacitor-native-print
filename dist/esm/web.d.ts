import { WebPlugin } from '@capacitor/core';
import type { NativePrintAvailabilityResult, NativePrintPlugin, PrintPdfOptions, PrintPdfResult, PrintCurrentPageOptions, PrintCurrentPageResult } from './definitions';
export declare class NativePrintWeb extends WebPlugin implements NativePrintPlugin {
    printPdf(_options: PrintPdfOptions): Promise<PrintPdfResult>;
    isAvailable(): Promise<NativePrintAvailabilityResult>;
    printCurrentPage(_options?: PrintCurrentPageOptions): Promise<PrintCurrentPageResult>;
}
//# sourceMappingURL=web.d.ts.map