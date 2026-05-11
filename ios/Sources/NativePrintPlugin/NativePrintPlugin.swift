import Foundation
import Capacitor
import UIKit
import WebKit

@objc(NativePrintPlugin)
public class NativePrintPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "NativePrintPlugin"
    public let jsName = "NativePrint"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "isAvailable", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "printCurrentPage", returnType: CAPPluginReturnPromise)
    ]

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
