package com.robusthomes.capacitor.nativeprint;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.android.PDFBoxResourceLoader;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.io.MemoryUsageSetting;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.io.RandomAccessBufferedFileInputStream;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.io.ScratchFile;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.pdfparser.PDFParser;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.cos.COSName;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.cos.COSBase;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.cos.COSArray;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.cos.COSStream;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.pdmodel.common.PDRectangle;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.pdmodel.PDDocument;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.pdmodel.PDPage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Private immutable snapshot, serialized disk I/O, vector-preserving page extraction. */
final class PdfFileAdapter extends PrintDocumentAdapter {
  static final class InputException extends Exception {
    final String code;
    InputException(String code) { this.code = code; }
  }
  private final File snapshot;
  private final File scratch;
  private final int pageCount;
  private final String name;
  private final ExecutorService worker;
  private final Runnable release;
  private final AtomicBoolean finished = new AtomicBoolean();

  PdfFileAdapter(Context context, String raw, String name, ExecutorService worker, Runnable release) throws Exception {
    this.name = name; this.worker = worker; this.release = release;
    if (raw == null) throw new InputException("INVALID_URI");
    Uri uri = Uri.parse(raw);
    if (!"file".equals(uri.getScheme()) || uri.getPath() == null || uri.getQuery() != null
        || uri.getFragment() != null || (uri.getAuthority() != null && !uri.getAuthority().isEmpty()))
      throw new InputException("INVALID_URI");
    File source = new File(uri.getPath()).getCanonicalFile();
    boolean allowed = false;
    for (File root : new File[]{context.getCacheDir(), context.getFilesDir(), context.getExternalCacheDir(), context.getExternalFilesDir(null)}) {
      if (root != null && source.getPath().startsWith(root.getCanonicalPath() + File.separator)) allowed = true;
    }
    if (!allowed) throw new InputException("INVALID_URI");
    if (!source.isFile() || !source.canRead()) throw new InputException("FILE_UNREADABLE");
    scratch = new File(context.getCacheDir(), "native-print-" + java.util.UUID.randomUUID());
    if (!scratch.mkdir()) throw new InputException("FILE_UNREADABLE");
    snapshot = new File(scratch, "document.pdf");
    try {
      try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(snapshot)) {
        byte[] header = new byte[8];
        if (in.read(header) != 8 || !new String(header, java.nio.charset.StandardCharsets.US_ASCII)
            .matches("%PDF-(1\\.[0-7]|2\\.0)"))
          throw new InputException("INVALID_PDF");
        out.write(header); copy(in, out, null);
      }
      PDFBoxResourceLoader.init(context.getApplicationContext());
      PdfDiagnostics.begin();
      try (PDDocument pdf = load()) {
        if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1) throw new InputException("INVALID_PDF");
        int actualPages = 0;
        for (PDPage page : pdf.getPages()) {
          if (!COSName.PAGE.equals(page.getCOSObject().getCOSName(COSName.TYPE)))
            throw new InputException("INVALID_PDF");
          PDRectangle box = page.getMediaBox();
          if (box == null || !Float.isFinite(box.getWidth()) || !Float.isFinite(box.getHeight())
              || box.getWidth() <= 0 || box.getHeight() <= 0) throw new InputException("INVALID_PDF");
          COSBase contents = page.getCOSObject().getDictionaryObject(COSName.CONTENTS);
          if (contents instanceof COSArray) {
            COSArray streams = (COSArray) contents;
            for (int i = 0; i < streams.size(); i++) if (!(streams.getObject(i) instanceof COSStream))
              throw new InputException("INVALID_PDF");
          } else if (contents != null && !(contents instanceof COSStream)) throw new InputException("INVALID_PDF");
          // Read every content stream through the actual decoder. Never log or
          // retain the decoded bytes; damaged compressed streams fail here.
          try (InputStream content = page.getContents()) {
            byte[] buffer = new byte[65536];
            while (content.read(buffer) != -1) { /* validate stream without retaining content */ }
          }
          actualPages++;
        }
        if (actualPages != pdf.getNumberOfPages()) throw new InputException("INVALID_PDF");
        PdfDiagnostics.requireClean();
        pageCount = actualPages;
      } finally { PdfDiagnostics.end(); }
    } catch (Exception error) {
      cleanup();
      if (error instanceof InputException) throw (InputException) error;
      throw new InputException("INVALID_PDF");
    }
  }
  private PDDocument load() throws IOException {
    RandomAccessBufferedFileInputStream input = new RandomAccessBufferedFileInputStream(snapshot);
    ScratchFile storage = null;
    PDDocument document = null;
    try {
      storage = new ScratchFile(MemoryUsageSetting.setupTempFileOnly().setTempDir(scratch));
      PDFParser parser = new PDFParser(input, "", storage);
      parser.setLenient(false);
      parser.parse();
      document = parser.getPDDocument();
      PdfDiagnostics.requireClean();
      return document; // PDDocument owns input and storage until close().
    } catch (Exception error) {
      if (document != null) try { document.close(); } catch (IOException ignored) { /* failure remains rejected */ }
      if (storage != null) try { storage.close(); } catch (IOException ignored) { /* failure remains rejected */ }
      try { input.close(); } catch (IOException ignored) { /* failure remains rejected */ }
      // Never propagate parser exception text, causes, paths, or PDF fragments.
      throw new IOException("Invalid PDF input.");
    }
  }
  @Override public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
      CancellationSignal cancel, LayoutResultCallback callback, Bundle extras) {
    if (cancel.isCanceled() || finished.get()) { callback.onLayoutCancelled(); return; }
    callback.onLayoutFinished(new PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
      .setPageCount(pageCount).build(), false);
  }
  @Override public void onWrite(PageRange[] ranges, ParcelFileDescriptor destination,
      CancellationSignal cancel, WriteResultCallback callback) {
    Runnable write = () -> {
      boolean cancelled = false;
      boolean failed = false;
      List<PageRange> actual = new ArrayList<>();
      try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(destination)) {
        if (cancel.isCanceled() || finished.get()) throw new InterruptedIOException();
        actual.addAll(java.util.Arrays.asList(writePdf(ranges, out, cancel)));
        if (cancel.isCanceled()) throw new InterruptedIOException();
      } catch (InterruptedIOException error) { cancelled = true; }
        catch (Exception error) { failed = true; }
      // Exactly one callback, after the descriptor is closed. Never report unwritten pages.
      if (cancelled || cancel.isCanceled()) callback.onWriteCancelled();
      else if (failed) callback.onWriteFailed("Unable to write PDF pages.");
      else callback.onWriteFinished(actual.toArray(new PageRange[0]));
    };
    try { worker.execute(write); }
    catch (java.util.concurrent.RejectedExecutionException error) {
      try { destination.close(); } catch (IOException ignored) { /* report rejection below */ }
      if (finished.get() || cancel.isCanceled()) callback.onWriteCancelled();
      else callback.onWriteFailed("Unable to write PDF pages.");
    }
  }
  PageRange[] writePdf(PageRange[] ranges, OutputStream out, CancellationSignal cancel) throws IOException {
    if (cancel.isCanceled() || finished.get()) throw new InterruptedIOException();
    PdfDiagnostics.begin();
    try {
    List<PageRange> actual = new ArrayList<>();
        boolean[] selected = selectPages(ranges, pageCount);
        int count = 0;
        for (int i = 0; i < pageCount; i++) if (selected[i]) {
          count++; actual.add(new PageRange(i, i));
        }
        if (count == 0) throw new IOException();
        if (count == pageCount) {
          try (InputStream in = new FileInputStream(snapshot)) { copy(in, out, cancel); }
        } else {
          try (PDDocument input = load(); PDDocument output = new PDDocument(MemoryUsageSetting.setupTempFileOnly().setTempDir(scratch))) {
            for (int i = 0; i < pageCount; i++) if (selected[i]) {
              if (cancel.isCanceled()) throw new InterruptedIOException();
              PDPage original = input.getPage(i);
              PDPage imported = output.importPage(original);
              imported.setResources(original.getResources());
              imported.setMediaBox(original.getMediaBox());
              imported.setCropBox(original.getCropBox());
              imported.setRotation(original.getRotation());
            }
            output.save(new FilterOutputStream(out) {
              @Override public void write(byte[] b, int off, int len) throws IOException {
                if (cancel.isCanceled()) throw new InterruptedIOException();
                out.write(b, off, len);
              }
              @Override public void write(int b) throws IOException {
                if (cancel.isCanceled()) throw new InterruptedIOException();
                out.write(b);
              }
            });
          }
        }
    if (cancel.isCanceled()) throw new InterruptedIOException();
    PdfDiagnostics.requireClean();
    return actual.toArray(new PageRange[0]);
    } catch (InterruptedIOException error) {
      throw new InterruptedIOException("PDF write cancelled.");
    } catch (Exception error) {
      throw new IOException("Unable to write PDF pages.");
    } finally { PdfDiagnostics.end(); }
  }
  static boolean[] selectPages(PageRange[] ranges, int count) {
    if (ranges == null || ranges.length == 0 || count < 1) throw new IllegalArgumentException("Invalid page range.");
    boolean[] selected = new boolean[count];
    for (PageRange range : ranges) {
      if (range == null || (!PageRange.ALL_PAGES.equals(range) && range.getEnd() >= count))
        throw new IllegalArgumentException("Invalid page range.");
      int end = Math.min(range.getEnd(), count - 1);
      for (int i = Math.max(0, range.getStart()); i <= end; i++) selected[i] = true;
    }
    return selected;
  }
  private static void copy(InputStream in, OutputStream out, CancellationSignal cancel) throws IOException {
    byte[] buffer = new byte[65536]; int n;
    while ((n = in.read(buffer)) != -1) {
      if (cancel != null && cancel.isCanceled()) throw new InterruptedIOException();
      out.write(buffer, 0, n);
    }
  }
  @Override public void onFinish() {
    if (!finished.compareAndSet(false, true)) return;
    worker.execute(() -> { try { cleanup(); } finally { release.run(); worker.shutdown(); } });
  }
  private void cleanup() {
    File[] files = scratch.listFiles();
    if (files != null) for (File file : files) file.delete();
    scratch.delete();
  }
}
