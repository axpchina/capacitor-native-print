import type { NativePrintPlugin, PrintPdfResult } from '../src/definitions';
declare const plugin: NativePrintPlugin;
const legacy: Promise<{ started: boolean; platform: 'ios' | 'android' | 'web' }> = plugin.printCurrentPage();
const withName = plugin.printCurrentPage({ jobName: 'Report' });
const pdf: Promise<PrintPdfResult> = plugin.printPdf({ fileUri: 'file:///private/report.pdf', jobName: 'Report' });
const noName: Promise<PrintPdfResult> = plugin.printPdf({ fileUri: 'file:///private/report.pdf' });
// @ts-expect-error fileUri is required
plugin.printPdf({ jobName: 'Report' });
// @ts-expect-error bytes/base64 are not the PDF API
plugin.printPdf({ fileUri: new Uint8Array() });
void [legacy, withName, pdf, noName];
