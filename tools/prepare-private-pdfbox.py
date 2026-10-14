#!/usr/bin/env python3
"""Reproduce the private Android PDF engine from three hash-pinned upstream inputs.
No network, binary rewriting, log filtering, or changes to a consumer's PDFBox.
"""
from pathlib import Path
import argparse, hashlib, json, re, zipfile, difflib

ROOT = Path(__file__).resolve().parents[1]
PREFIX = 'com.robusthomes.capacitor.nativeprint.internal.pdfboxport'
NOTICE = '// Modified by Robust Homes: private namespace and content-free diagnostics. See android/vendor/README.md.\n'

def code_mask(text):
    # Keep positions and Java punctuation; hide strings, char literals and comments.
    return re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*',
                  lambda m: ''.join('\n' if c == '\n' else ' ' for c in m.group()), text)

def replace_calls(text):
    masked = code_mask(text)
    pattern = r'\bLog\.([diew])\s*\(|\bSystem\.(?:out|err)\.print(?:ln)?\s*\(|\b\w+\.printStackTrace\s*\('
    edits=[]
    for m in re.finditer(pattern,masked):
        depth=1; end=m.end()
        while depth:
            if masked[end]=='(': depth+=1
            elif masked[end]==')':depth-=1
            end+=1
        while masked[end].isspace():end+=1
        assert masked[end]==';', 'Only standalone diagnostic statements may be replaced'
        kind=m.group(1)
        replacement='com.robusthomes.capacitor.nativeprint.PdfDiagnostics.warning();' if kind in ('w','e') or 'printStackTrace' in m.group() else ';'
        edits.append((m.start(),end+1,replacement))
    for start,end,value in reversed(edits):text=text[:start]+value+text[end:]
    text=text.replace('import android.util.Log;','')
    return text,len(edits)

def replace_method_body(text,signature,body):
    start=text.index('{',text.index(signature)); mask=code_mask(text);depth=1;end=start+1
    while depth:
        if mask[end]=='{':depth+=1
        elif mask[end]=='}':depth-=1
        end+=1
    return text[:start+1]+'\n'+body+'\n    '+text[end-1:]

def prepare(inputs):
    lock=json.loads((ROOT/'android/vendor/upstream-lock.json').read_text())
    for name,path in inputs.items():
        assert hashlib.sha256(path.read_bytes()).hexdigest()==lock[name]['sha256'], 'Upstream input hash mismatch: '+name
    source=zipfile.ZipFile(inputs['source']); tag=zipfile.ZipFile(inputs['tag']); aar=zipfile.ZipFile(inputs['aar'])
    tagroot='PdfBox-Android-2.0.27.0/'
    files={};counts={};patch=[]
    for name in source.namelist():
        if not name.endswith('.java'):continue
        data=source.read(name)
        assert data==tag.read(tagroot+'library/src/main/java/'+name), 'Maven source and release tag differ'
        original=data.decode('utf-8');text,count=replace_calls(original)
        if count:counts[name]=count
        if name.endswith('/COSParser.java'):
            # The public NativePrint contract rejects every encrypted PDF. Refuse
            # before decoding any encryption dictionary or invoking crypto providers.
            text=replace_method_body(text,'private void prepareDecryption()',
                '        COSBase item = document.getTrailer().getItem(COSName.ENCRYPT);\n'
                '        if (item != null && !(item instanceof COSNull))\n'
                '            throw new IOException("Encrypted PDF is unsupported.");')
        if name.endswith('/JPXFilter.java'):
            # The original AAR only compile-depended on the optional JP2 codec,
            # which was absent from the QA host. Keep its unsupported behavior.
            text=re.sub(r'import com\.gemalto\.jp2\.[^;]+;','',text)
            text=replace_method_body(text,'public DecodeResult decode(',
                '        throw new IOException("JPEG2000 decoding is unsupported.");')
            text=replace_method_body(text,'protected void encode(',
                '        throw new IOException("JPEG2000 encoding is unsupported.");')
            # readJPX is only called by the replaced decode method and references
            # the optional codec; remove its implementation while retaining signature.
            text=replace_method_body(text,'private Bitmap readJPX(',
                '        throw new IOException("JPEG2000 decoding is unsupported.");')
        if text!=original:patch.extend(difflib.unified_diff(original.splitlines(True),text.splitlines(True),fromfile=name,tofile=name))
        text=NOTICE+text.replace('com.tom_roush',PREFIX).replace('com/tom_roush',PREFIX.replace('.','/'))
        assert not re.search(r'android\.util\.Log|System\.(?:out|err)|\.printStackTrace\s*\(',code_mask(text)), name
        files['java/'+name.replace('com/tom_roush',PREFIX.replace('.','/'))]=text.encode()
    for name in aar.namelist():
        if name.startswith('assets/') and not name.endswith('/'):
            files[name.replace('com/tom_roush',PREFIX.replace('.','/'))]=aar.read(name)
    for name in ('LICENSE.txt','NOTICE.txt'):
        data=tag.read(tagroot+name)
        (ROOT/'android/vendor'/name).write_bytes(data)
        files['assets/nativeprint-licenses/pdfbox-'+name]=data
    output=ROOT/'android/vendor/pdfbox-private-2.0.27.0-r1.zip'
    with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16
            z.writestr(info,data)
    (ROOT/'android/vendor/security-patch.diff').write_text(''.join(patch))
    manifest={'privateArchiveSha256':hashlib.sha256(output.read_bytes()).hexdigest(),'removedDiagnosticCalls':sum(counts.values()),'callSitesByFile':counts,'files':{n:hashlib.sha256(b).hexdigest() for n,b in sorted(files.items())}}
    (ROOT/'android/vendor/private-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps({'archiveSha256':manifest['privateArchiveSha256'],'removedDiagnosticCalls':manifest['removedDiagnosticCalls'],'files':len(files)}))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--tag',type=Path,required=True);p.add_argument('--aar',type=Path,required=True)
    prepare(vars(p.parse_args()))
