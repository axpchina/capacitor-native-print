package com.robusthomes.capacitor.nativeprint;

import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "NativePrint")
public class NativePrintPlugin extends Plugin {
  @PluginMethod
  public void isAvailable(PluginCall call) {
    WebView webView = getBridge() != null ? getBridge().getWebView() : null;
    Context context = getContext();
    PrintManager printManager = context != null
      ? (PrintManager) context.getSystemService(Context.PRINT_SERVICE)
      : null;

    JSObject result = new JSObject();
    result.put("available", webView != null && getActivity() != null && printManager != null);
    result.put("platform", "android");

    if (webView == null || getActivity() == null) {
      result.put("reason", "The current page is not available for printing.");
    } else if (printManager == null) {
      result.put("reason", "Android print services are not available on this device.");
    }

    call.resolve(result);
  }

  @PluginMethod
  public void printCurrentPage(PluginCall call) {
    WebView webView = getBridge() != null ? getBridge().getWebView() : null;

    if (webView == null || getActivity() == null) {
      call.reject("The current page is not available for printing.", "UNAVAILABLE");
      return;
    }

    String fallbackName = getActivity().getTitle() != null ? getActivity().getTitle().toString() : "Document";
    String jobName = call.getString("jobName", fallbackName);

    getActivity().runOnUiThread(() -> {
      try {
        PrintManager printManager = (PrintManager) getActivity().getSystemService(Context.PRINT_SERVICE);

        if (printManager == null) {
          call.reject("Android print services are not available on this device.", "SERVICE_UNAVAILABLE");
          return;
        }

        PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(jobName);
        printManager.print(jobName, adapter, new PrintAttributes.Builder().build());

        JSObject result = new JSObject();
        result.put("started", true);
        result.put("platform", "android");
        call.resolve(result);
      } catch (Exception exception) {
        call.reject("Unable to open the Android print dialog.", "PRINT_FAILED", exception);
      }
    });
  }
}
