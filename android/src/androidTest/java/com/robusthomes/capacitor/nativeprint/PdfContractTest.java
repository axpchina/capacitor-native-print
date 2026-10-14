package com.robusthomes.capacitor.nativeprint;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.*;
import android.graphics.pdf.PdfDocument;
import android.graphics.Paint;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.pdmodel.PDDocument;
import com.robusthomes.capacitor.nativeprint.internal.pdfboxport.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.util.concurrent.*;

/** Standalone instrumentation: synthetic PDFs only, no account, network or print submission. */
public class PdfContractTest extends Instrumentation {
  @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
  private void check(boolean value) { if (!value) throw new AssertionError(); }
  @Override public void onStart() {
    Bundle result = new Bundle();
    result.putString("processId", String.valueOf(android.os.Process.myPid()));
    ExecutorService worker = Executors.newSingleThreadExecutor();
    File input = new File(getTargetContext().getCacheDir(), "contract-input.pdf");
    try {
      PdfDocument pdf = new PdfDocument();
      try (OutputStream out = new FileOutputStream(input)) {
        for (int i=1;i<=3;i++) {
          PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(400,600,i).create());
          page.getCanvas().drawText("SYNTHETIC_PAGE_"+i,30,40,new Paint()); pdf.finishPage(page);
        }
        pdf.writeTo(out);
      } finally {pdf.close();}
      for (String uri : new String[]{"https://example.com/a.pdf", "file:///etc/passwd", "content://foreign/a.pdf"}) {
        try { new PdfFileAdapter(getTargetContext(),uri,"Test",worker,()->{}); throw new AssertionError(); }
        catch (PdfFileAdapter.InputException e) {check(e.code.equals("INVALID_URI"));}
      }
      try {new PdfFileAdapter(getTargetContext(),new File(input.getParentFile(),"absent.pdf").toURI().toString(),"Test",worker,()->{});throw new AssertionError();}
      catch (PdfFileAdapter.InputException e) { check(e.code.equals("FILE_UNREADABLE")); }
      File invalid=new File(input.getParentFile(),"invalid.pdf");
      try(OutputStream out=new FileOutputStream(invalid)){out.write("not a PDF".getBytes());}
      try {new PdfFileAdapter(getTargetContext(),invalid.toURI().toString(),"Test",worker,()->{});throw new AssertionError();}
      catch (PdfFileAdapter.InputException e) {check(e.code.equals("INVALID_PDF"));} finally {invalid.delete();}
      CountDownLatch released=new CountDownLatch(1);
      PdfFileAdapter adapter=new PdfFileAdapter(getTargetContext(),input.toURI().toString(),"Test",worker,released::countDown);
      // Original lifetime is independent of the snapshot after construction.
      input.delete();
      write(adapter,new PageRange[]{PageRange.ALL_PAGES},false,3,"SYNTHETIC_PAGE_1",null);
      write(adapter,new PageRange[]{new PageRange(1,1)},false,1,"SYNTHETIC_PAGE_2","SYNTHETIC_PAGE_1");
      write(adapter,new PageRange[]{new PageRange(0,0),new PageRange(2,2)},false,2,"SYNTHETIC_PAGE_3","SYNTHETIC_PAGE_2");
      write(adapter,new PageRange[]{PageRange.ALL_PAGES},true,0,null,null);
      write(adapter,new PageRange[]{PageRange.ALL_PAGES},false,3,"SYNTHETIC_PAGE_3",null);
      adapter.onFinish();check(released.await(10,TimeUnit.SECONDS));
      File[] remaining=getTargetContext().getCacheDir().listFiles((dir,name)->name.startsWith("native-print-"));
      check(remaining!=null && remaining.length==0);
      int securityCases = PdfSecurityCases.run(getTargetContext());
      result.putString("securityCases", String.valueOf(securityCases));
      result.putString("stream","PASS: remote/traversal/content URI rejection; missing/non-PDF rejection; all pages; single page; disjoint pages; cancellation; retry; snapshot lifetime; cleanup\n");
      finish(Activity.RESULT_OK,result);
    } catch(Throwable error) {
      result.putString("stream","FAIL: "+error.getClass().getSimpleName()+(error instanceof AssertionError ? "; "+error.getMessage() : "")+"\n");finish(Activity.RESULT_CANCELED,result);
    } finally {input.delete();worker.shutdown();}
  }
  private void write(PdfFileAdapter adapter,PageRange[] ranges,boolean cancelled,int count,String present,String absent) throws Exception {
    File output=File.createTempFile("pdf-contract-",".pdf",getTargetContext().getCacheDir());
    CancellationSignal signal=new CancellationSignal();if(cancelled)signal.cancel();
    try(OutputStream out=new FileOutputStream(output)) {
      try {
        PageRange[] actual=adapter.writePdf(ranges,out,signal);
        check(!cancelled);check(actual.length==count);
      } catch(InterruptedIOException error) {check(cancelled);}
    }
    if(!cancelled)try(PDDocument pdf=PDDocument.load(output)){
      check(pdf.getNumberOfPages()==count);String text=new PDFTextStripper().getText(pdf);
      check(text.contains(present));if(absent!=null)check(!text.contains(absent));
      for(int i=0;i<count;i++){check(pdf.getPage(i).getMediaBox().getWidth()==400);check(pdf.getPage(i).getMediaBox().getHeight()==600);}
    }
    output.delete();
  }
}
