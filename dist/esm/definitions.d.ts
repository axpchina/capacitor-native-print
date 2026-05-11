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
    isAvailable(): Promise<NativePrintAvailabilityResult>;
    printCurrentPage(options?: PrintCurrentPageOptions): Promise<PrintCurrentPageResult>;
}
//# sourceMappingURL=definitions.d.ts.map