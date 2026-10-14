package com.robusthomes.capacitor.nativeprint;

import android.content.Context;
import android.os.CancellationSignal;
import android.print.PageRange;
import android.system.Os;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Bounded synthetic malformed corpus. No raw inputs or exception text are logged. */
final class PdfSecurityCases {
  static final String CANARY = "NP_SECURITY_R1_SYNTHETIC_";
  private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.US_ASCII); }
  private static void check(boolean value) { if (!value) throw new AssertionError("Security contract failed"); }
  private static byte[] pdf(String page, String content, String extraTrailer) throws IOException {
    String[] objects = {"<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>", page,
      "<< /Length " + bytes(content).length + " >>\nstream\n"+content+"\nendstream"};
    ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(bytes("%PDF-1.4\n"));
    List<Integer> offsets=new ArrayList<>();
    for(int i=0;i<objects.length;i++){offsets.add(out.size());out.write(bytes((i+1)+" 0 obj\n"+objects[i]+"\nendobj\n"));}
    int xref=out.size();out.write(bytes("xref\n0 5\n0000000000 65535 f \n"));
    for(int offset:offsets)out.write(bytes(String.format(Locale.ROOT,"%010d 00000 n \n",offset)));
    out.write(bytes("trailer\n<< /Size 5 /Root 1 0 R "+extraTrailer+" >>\nstartxref\n"+xref+"\n%%EOF\n"));
    return out.toByteArray();
  }
  static int run(Context context) throws Exception {
    String page="<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 600] /Contents 4 0 R >>";
    byte[] valid=pdf(page,"q\nQ","");
    Map<String,byte[]> cases=new LinkedHashMap<>();
    cases.put("invalid-header",bytes(CANARY+"HEADER"));
    cases.put("malformed-structure",bytes("%PDF-1.4\n"+CANARY+"STRUCTURE\n%%EOF"));
    String head="%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\n";
    cases.put("historical-xref-reproduction",bytes(head+"xref\n"+CANARY+"XREF\ntrailer\n<< /Root 1 0 R /Size 3 >>\nstartxref\n"+head.length()+"\n%%EOF\n"));
    cases.put("truncated",Arrays.copyOf(pdf(page,"% "+CANARY+"TRUNCATED\nq\nQ",""),150));
    cases.put("invalid-reference",pdf(page.replace("/Contents 4 0 R","/Contents ("+CANARY+"REF) 0 R"),"q\nQ",""));
    cases.put("unsupported-version",bytes(new String(valid,StandardCharsets.US_ASCII).replace("%PDF-1.4","%PDF-9.9")+CANARY+"VERSION"));
    cases.put("encrypted",pdf(page,"q\nQ","/Encrypt << /Filter /Standard /V 4 /R 4 /Note ("+CANARY+"ENCRYPTED) >>"));
    cases.put("damaged-page-metadata",pdf(page.replace("[0 0 400 600]","[0 0 ("+CANARY+"BOX) 600]"),"q\nQ",""));
    cases.put("invalid-page-type",pdf(page.replace("/Type /Page ","/Type /"+CANARY+"TYPE "),"q\nQ",""));
    // Reject a malformed content stream dictionary.
    cases.put("broken-stream",pdf(page.replace("/Contents 4 0 R","/Contents << /Length 1 /Filter /"+CANARY+"FILTER >>"),"q\nQ",""));
    File input=new File(context.getCacheDir(),"nativeprint-security-input.pdf");
    int tested=0;
    for(Map.Entry<String,byte[]> entry:cases.entrySet()) {
      Files.write(input.toPath(),entry.getValue());
      ExecutorService worker=Executors.newSingleThreadExecutor();
      try {
        PdfFileAdapter adapter=null;
        try { adapter=new PdfFileAdapter(context,input.toURI().toString(),"Security test",worker,()->{}); }
        catch(Exception error) {
          for(Throwable e=error;e!=null;e=e.getCause())check(!e.toString().contains(CANARY));
          check(error instanceof PdfFileAdapter.InputException);
          check(((PdfFileAdapter.InputException)error).code.equals("INVALID_PDF"));
          tested++;continue;
        }
        adapter.onFinish();throw new AssertionError("Malformed input accepted: "+entry.getKey());
      } finally { worker.shutdown();worker.awaitTermination(10,TimeUnit.SECONDS);input.delete(); }
    }
    // File I/O denial must be a fixed code and leave no active snapshot.
    Files.write(input.toPath(),valid);Os.chmod(input.getPath(),0);
    ExecutorService denied=Executors.newSingleThreadExecutor();
    try { new PdfFileAdapter(context,input.toURI().toString(),"Test",denied,()->{});throw new AssertionError("Unreadable input accepted"); }
    catch(PdfFileAdapter.InputException error){check(error.code.equals("FILE_UNREADABLE"));tested++;}
    finally{Os.chmod(input.getPath(),0600);input.delete();denied.shutdown();}
    // Invalid selection and cancellation cannot poison a subsequent valid write.
    Files.write(input.toPath(),valid);ExecutorService worker=Executors.newSingleThreadExecutor();CountDownLatch released=new CountDownLatch(1);
    PdfFileAdapter adapter=new PdfFileAdapter(context,input.toURI().toString(),"Test",worker,released::countDown);
    try {
      for(PageRange[] range:new PageRange[][]{null,new PageRange[0],{null},{new PageRange(1,1)}}) {
        try { adapter.writePdf(range,new ByteArrayOutputStream(),new CancellationSignal());throw new AssertionError("Invalid range accepted"); }
        catch(IOException expected){check(!expected.toString().contains(CANARY));tested++;}
      }
      ByteArrayOutputStream out=new ByteArrayOutputStream();adapter.writePdf(new PageRange[]{PageRange.ALL_PAGES},out,new CancellationSignal());check(Arrays.equals(out.toByteArray(),valid));tested++;
    } finally {adapter.onFinish();check(released.await(10,TimeUnit.SECONDS));input.delete();}
    File[] remaining=context.getCacheDir().listFiles((dir,name)->name.startsWith("native-print-"));check(remaining!=null&&remaining.length==0);
    return tested;
  }
}
