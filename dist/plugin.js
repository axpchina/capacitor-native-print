import { registerPlugin, WebPlugin } from '@capacitor/core';

const NativePrint = registerPlugin('NativePrint', {
    web: () => Promise.resolve().then(function () { return web; }).then(m => new m.NativePrintWeb()),
});

const createPluginError = (code, message) => {
    const error = new Error(message);
    error.code = code;
    return error;
};
class NativePrintWeb extends WebPlugin {
    async isAvailable() {
        const available = typeof window !== 'undefined' && typeof window.print === 'function';
        return {
            available,
            platform: 'web',
            reason: available ? undefined : 'window.print is not available in this environment.',
        };
    }
    async printCurrentPage(_options) {
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
        }
        catch (error) {
            const message = error instanceof Error ? error.message : 'Unable to open the browser print dialog.';
            throw createPluginError('PRINT_FAILED', message);
        }
    }
}

var web = /*#__PURE__*/Object.freeze({
    __proto__: null,
    NativePrintWeb: NativePrintWeb
});

export { NativePrint };
//# sourceMappingURL=plugin.js.map
