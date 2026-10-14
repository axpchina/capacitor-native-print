import Foundation
import Capacitor
import UIKit
import WebKit

@objc(NativePrintPlugin)
public class NativePrintPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "NativePrintPlugin"
    public let jsName = "NativePrint"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "printPdf", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isAvailable", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "printCurrentPage", returnType: CAPPluginReturnPromise)
    ]

    private var pdfBusy = false
    private var pdfController: UIPrintInteractionController?

    @objc func printPdf(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { call.reject("Printing unavailable.", "UNAVAILABLE"); return }
            guard !self.pdfBusy else { call.reject("A PDF print request is active.", "BUSY"); return }
            guard UIPrintInteractionController.isPrintingAvailable,
                  let presenter = self.bridge?.viewController,
                  presenter.viewIfLoaded?.window != nil,
                  presenter.presentedViewController == nil else {
                call.reject("Print UI is unavailable or another dialog is active.", "UNAVAILABLE"); return
            }
            guard let raw = call.getString("fileUri"), let source = URL(string: raw),
                  source.isFileURL, source.host == nil || source.host == "" || source.host == "localhost",
                  source.query == nil, source.fragment == nil else {
                call.reject("An app-private file URI is required.", "INVALID_URI"); return
            }
            self.pdfBusy = true
            DispatchQueue.global(qos: .userInitiated).async {
                var snapshot: URL?
                do {
                    let fm = FileManager.default
                    let resolved = source.standardizedFileURL.resolvingSymlinksInPath()
                    let roots = [fm.temporaryDirectory] + fm.urls(for: .cachesDirectory, in: .userDomainMask)
                        + fm.urls(for: .documentDirectory, in: .userDomainMask)
                    guard roots.contains(where: { resolved.path.hasPrefix($0.resolvingSymlinksInPath().path + "/") }) else {
                        throw PdfInputError.invalidURI
                    }
                    guard fm.isReadableFile(atPath: resolved.path),
                          (try resolved.resourceValues(forKeys: [.isRegularFileKey])).isRegularFile == true else {
                        throw PdfInputError.unreadable
                    }
                    let directory = fm.temporaryDirectory.appendingPathComponent("native-print-" + UUID().uuidString)
                    try fm.createDirectory(at: directory, withIntermediateDirectories: false)
                    snapshot = directory.appendingPathComponent("document.pdf")
                    try fm.copyItem(at: resolved, to: snapshot!)
                    guard let document = CGPDFDocument(snapshot! as CFURL), !document.isEncrypted,
                          document.numberOfPages > 0 else { throw PdfInputError.invalidPDF }
                    let ready = snapshot!
                    DispatchQueue.main.async {
                        guard presenter.viewIfLoaded?.window != nil, presenter.presentedViewController == nil,
                              UIPrintInteractionController.canPrint(ready) else {
                            try? fm.removeItem(at: directory)
                            self.pdfBusy = false
                            call.reject("PDF print UI unavailable.", "UNAVAILABLE"); return
                        }
                        // Use UIKit's shared controller, with PDF-only content for this interaction.
                        let controller = UIPrintInteractionController.shared
                        controller.printFormatter = nil
                        controller.printPageRenderer = nil
                        controller.printingItems = nil
                        self.pdfController = controller
                        let info = UIPrintInfo(dictionary: nil)
                        info.jobName = call.getString("jobName") ?? "Document"
                        info.outputType = .general
                        controller.printInfo = info
                        controller.printingItem = ready
                        var finished = false
                        let finish: (Bool, Bool) -> Void = { completed, failed in
                            guard !finished else { return }; finished = true
                            controller.printingItem = nil
                            self.pdfController = nil
                            self.pdfBusy = false
                            try? fm.removeItem(at: directory)
                            if failed { call.reject("PDF print interaction failed.", "PRINT_FAILED") }
                            else { call.resolve(["platform": "ios", "status": completed ? "interaction_completed" : "cancelled"]) }
                        }
                        let completion: UIPrintInteractionController.CompletionHandler = { _, completed, error in
                            finish(completed, error != nil)
                        }
                        let presented: Bool
                        if UIDevice.current.userInterfaceIdiom == .pad {
                            presented = controller.present(from: presenter.view.bounds, in: presenter.view,
                                                           animated: true, completionHandler: completion)
                        } else { presented = controller.present(animated: true, completionHandler: completion) }
                        if !presented { finish(false, true) }
                    }
                } catch {
                    if let snapshot { try? FileManager.default.removeItem(at: snapshot.deletingLastPathComponent()) }
                    let code = (error as? PdfInputError)?.code ?? "FILE_UNREADABLE"
                    DispatchQueue.main.async {
                        self.pdfBusy = false
                        call.reject("PDF input could not be prepared.", code)
                    }
                }
            }
        }
    }

    private enum PdfInputError: Error {
        case invalidURI, unreadable, invalidPDF
        var code: String {
            switch self {
            case .invalidURI: return "INVALID_URI"
            case .unreadable: return "FILE_UNREADABLE"
            case .invalidPDF: return "INVALID_PDF"
            }
        }
    }

    @objc func isAvailable(_ call: CAPPluginCall) {
        let webView = bridge?.webView
        let available = webView != nil

        if available {
            call.resolve([
                "available": true,
                "platform": "ios"
            ])
            return
        }

        call.resolve([
            "available": false,
            "platform": "ios",
            "reason": "The current page is not available for printing."
        ])
    }

    @objc func printCurrentPage(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            guard let self,
                  let webView = self.bridge?.webView else {
                call.reject("The current page is not available for printing.", "UNAVAILABLE", nil)
                return
            }

            let printController = UIPrintInteractionController.shared
            let formatter = webView.viewPrintFormatter()
            let printInfo = UIPrintInfo(dictionary: nil)
            let jobName = call.getString("jobName") ?? "Document"

            printInfo.jobName = jobName
            printInfo.outputType = .general
            printController.printInfo = printInfo
            printController.printFormatter = formatter
            printController.showsNumberOfCopies = true

            let completion: UIPrintInteractionController.CompletionHandler = { _, completed, error in
                if let error {
                    call.reject("Unable to open the iPhone print dialog.", "PRINT_FAILED", error)
                    return
                }

                call.resolve([
                    "started": completed,
                    "platform": "ios"
                ])
            }

            if UIDevice.current.userInterfaceIdiom == .pad {
                guard let viewController = self.bridge?.viewController else {
                    call.reject("The current page is not available for printing.", "UNAVAILABLE", nil)
                    return
                }

                printController.present(
                    from: viewController.view.bounds,
                    in: viewController.view,
                    animated: true,
                    completionHandler: completion
                )
            } else {
                printController.present(animated: true, completionHandler: completion)
            }
        }
    }
}
