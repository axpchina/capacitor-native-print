package com.robusthomes.capacitor.nativeprint;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.concurrent.*;

public class PdfDiagnosticsTest {
  @Test public void warningRejectsOperationAndRetryClearsIt() throws Exception {
    PdfDiagnostics.begin();
    PdfDiagnostics.requireClean();
    PdfDiagnostics.warning();
    try { PdfDiagnostics.requireClean(); fail(); }
    catch (IOException error) {
      assertEquals("Invalid PDF structure.", error.getMessage());
      assertNull(error.getCause());
    } finally { PdfDiagnostics.end(); }
    PdfDiagnostics.begin();
    PdfDiagnostics.requireClean();
    PdfDiagnostics.end();
  }
  @Test public void warningIsIsolatedBetweenConcurrentJobs() throws Exception {
    ExecutorService worker = Executors.newSingleThreadExecutor();
    PdfDiagnostics.begin();
    PdfDiagnostics.warning();
    try {
      assertTrue(worker.submit(() -> {
        PdfDiagnostics.begin();
        try { PdfDiagnostics.requireClean(); return true; }
        finally { PdfDiagnostics.end(); }
      }).get(5, TimeUnit.SECONDS));
      try { PdfDiagnostics.requireClean(); fail(); } catch (IOException expected) { }
    } finally { PdfDiagnostics.end(); worker.shutdown(); }
  }
}
