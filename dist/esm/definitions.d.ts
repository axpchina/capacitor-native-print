export type NativePrintPlatform = 'ios' | 'android' | 'web';
export interface PrintCurrentPageOptions {
    jobName?: string;
}
export interface PrintCurrentPageResult {
    started: boolean;
    platform: NativePrintPlatform;
}
export interface NativePrintAvailabilityResult {
    available: boolean;
    platform: NativePrintPlatform;
    reason?: string;
}
export interface NativePrintPlugin {
    printPdf(options: PrintPdfOptions): Promise<PrintPdfResult>;
    isAvailable(): Promise<NativePrintAvailabilityResult>;
    printCurrentPage(options?: PrintCurrentPageOptions): Promise<PrintCurrentPageResult>;
}
/** An existing PDF in app-private cache, files/Documents or temporary storage. */
export interface PrintPdfOptions {
    fileUri: string;
    jobName?: string;
}
/** Observable handoff only. Neither outcome proves physical paper completion. */
export interface PrintPdfResult {
    platform: 'ios' | 'android';
    status: 'interaction_completed' | 'cancelled' | 'handed_off';
}
//# sourceMappingURL=definitions.d.ts.map