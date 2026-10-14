package com.robusthomes.capacitor.nativeprint;

import java.io.IOException;

/** Content-free signal from the private parser. There is deliberately no log sink.
 * Warning arguments are removed at source, before their expressions are evaluated.
 * Each serialized parser operation resets and clears this per-thread flag.
 */
public final class PdfDiagnostics {
  private static final ThreadLocal<Boolean> damaged = new ThreadLocal<>();
  private PdfDiagnostics() {}
  public static void warning() { damaged.set(Boolean.TRUE); }
  static void begin() { damaged.remove(); }
  static void requireClean() throws IOException {
    if (Boolean.TRUE.equals(damaged.get())) throw new IOException("Invalid PDF structure.");
  }
  static void end() { damaged.remove(); }
}
