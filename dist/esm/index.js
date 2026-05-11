import { registerPlugin } from '@capacitor/core';
const NativePrint = registerPlugin('NativePrint', {
    web: () => import('./web').then(m => new m.NativePrintWeb()),
});
export * from './definitions';
export { NativePrint };
//# sourceMappingURL=index.js.map