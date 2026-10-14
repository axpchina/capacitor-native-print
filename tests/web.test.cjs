const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const { WebPlugin } = require('@capacitor/core');
function web(window) {
  const exports = {};
  const compiled = ts.transpileModule(fs.readFileSync(require('node:path').join(__dirname,'../src/web.ts'),'utf8'), {compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText;
  vm.runInNewContext(compiled, {exports, require:()=>({WebPlugin}), window});
  return new exports.NativePrintWeb();
}
test('legacy browser print is invoked once and keeps its public result', async()=>{
  let calls=0; const plugin=web({print(){calls++;}});
  assert.equal((await plugin.isAvailable()).available,true);
  assert.equal((await plugin.printCurrentPage()).started,true); assert.equal(calls,1);
});
test('legacy unavailable and failing print preserve errors', async()=>{
  await assert.rejects(web(undefined).printCurrentPage(),{code:'UNAVAILABLE'});
  await assert.rejects(web({print(){throw new Error('failed');}}).printCurrentPage(),{code:'PRINT_FAILED'});
});
test('PDF fallback rejects and never prints unrelated browser content',async()=>{
  let calls=0; const plugin=web({print(){calls++;}});
  for (const fileUri of ['file:///private/report.pdf','https://example.com/report.pdf',''])
    await assert.rejects(plugin.printPdf({fileUri}),{code:'UNAVAILABLE'});
  assert.equal(calls,0);
});
