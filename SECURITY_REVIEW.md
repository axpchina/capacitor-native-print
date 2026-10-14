# NativePrint 0.2.0 scoped security review

The supported Android path prints an unencrypted PDF already owned by the app.
It does not download remote input, extract attachments, verify certificates,
parse PEM keys, or provide an encryption/signature API. This review applies to
that path, not arbitrary direct use of internal engine classes or host crypto APIs.

## Engine boundary

The private PDFBox Android 2.0.27.0 archive is hash fenced and relocated. The
source transform removes diagnostic output statements and their argument
expressions; warning/error sites retain a non-content boolean rejection signal.
The original upstream AAR is excluded. Runtime class scanning and the 16-case
Android security corpus are required for release; passing this finite corpus
does not establish safety for every possible adversarial document.

The Apache security table lists CVE-2026-23907 and CVE-2026-33929 against the
ExtractEmbeddedFiles example. That example is absent from this archive and
this plugin does not extract embedded files. Earlier listed parser issues were
fixed before the pinned 2.0.27 source line. The Android port is independently
patched; this is not a claim of blanket upstream security certification.
Source: https://pdfbox.apache.org/security.html

## Retained crypto dependencies

The three Bouncy Castle 1.72 libraries are retained for engine class linkage.
They are historical dependencies with published advisories, not CVE-free
versions. CVE-2023-33202 concerns crafted ASN.1/PEM processing; additional
upstream advisories cover cryptographic operations. Do not use the plugin's
transitive versions as a security recommendation for other host operations.
Sources: https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902023%E2%80%9033202
and https://github.com/bcgit/bc-java/wiki

In the packaged engine, Bouncy Castle imports occur only in
PublicKeySecurityHandler and SecurityProvider. COSParser.prepareDecryption
rejects any non-null Encrypt trailer entry with a fixed error before dispatching
a security handler. PdfFileAdapter neither protects documents nor verifies
signatures. The writer calls encryption handlers only when encryption is set;
selected-page output is a new unencrypted document. Therefore these crypto
operations are not reachable through the supported plugin print path. This is
a source-based reachability conclusion, not an assertion that the dependency
versions themselves are free of vulnerabilities. Host applications using those
libraries directly require their own dependency resolution and security review.

## Remaining maintenance obligations

Use the documented app-owned generated-PDF boundary. Strict parsing and temporary-file scratch storage do not constitute a universal
CPU/memory sandbox for arbitrary hostile PDFs. Re-review upstream advisories, call reachability, output sinks,
archive hashes and cancellation/resource ownership on engine changes. Do not
silently restore the unpatched engine or broaden accepted input types.
The Android 17 long-dwell deferral does not waive any security gate.
