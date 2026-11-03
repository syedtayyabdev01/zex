import SwiftUI
import WebKit
import UIKit

private let homeURL = URL(string: "https://zxcstream.com/")!

struct ContentView: View {
    @StateObject private var browser = ZEXWebView()

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Text("ZEX")
                    .font(.system(size: 22, weight: .heavy, design: .rounded))
                    .foregroundStyle(Color(red: 0.90, green: 0.04, blue: 0.08))
                    .padding(.horizontal, 8)

                Rectangle()
                    .fill(.white.opacity(0.18))
                    .frame(width: 1, height: 28)

                toolbarButton("chevron.left", label: "Back") { browser.goBack() }
                    .disabled(!browser.canGoBack)
                toolbarButton("house", label: "Home") { browser.load(homeURL) }
                toolbarButton("arrow.clockwise", label: "Reload") { browser.reload() }
                Spacer(minLength: 4)
                Text("iOS TEST")
                    .font(.system(size: 10, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white.opacity(0.58))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background {
                RoundedRectangle(cornerRadius: 22)
                    .fill(.ultraThinMaterial)
                    .overlay(RoundedRectangle(cornerRadius: 22).stroke(.white.opacity(0.24), lineWidth: 1))
            }
            .padding(.horizontal, 14)
            .padding(.top, 8)
            .padding(.bottom, 8)
            .background(Color(red: 0.06, green: 0.06, blue: 0.07))

            BrowserView(browser: browser)
                .background(Color.black)
        }
        .background(Color.black.ignoresSafeArea())
    }

    private func toolbarButton(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 38, height: 36)
                .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 13))
                .overlay(RoundedRectangle(cornerRadius: 13).stroke(.white.opacity(0.14), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

@MainActor
final class ZEXWebView: ObservableObject {
    let view: WKWebView
    @Published private(set) var canGoBack = false
    private var navigationDelegate: WebNavigationDelegate!

    init() {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.websiteDataStore = .default()
        view = WKWebView(frame: .zero, configuration: configuration)
        view.allowsBackForwardNavigationGestures = true
        view.isOpaque = false
        view.backgroundColor = .black
        view.scrollView.backgroundColor = .black
        navigationDelegate = WebNavigationDelegate(owner: self)
        view.navigationDelegate = navigationDelegate
        view.uiDelegate = WebUIDelegate()
        load(homeURL)
    }

    func load(_ url: URL) { view.load(URLRequest(url: url)) }
    func reload() { view.reload() }
    func goBack() { if view.canGoBack { view.goBack() } }
    func updateNavigationState() { canGoBack = view.canGoBack }
}

private struct BrowserView: UIViewRepresentable {
    let browser: ZEXWebView
    func makeUIView(context: Context) -> WKWebView { browser.view }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

private final class WebNavigationDelegate: NSObject, WKNavigationDelegate {
    weak var owner: ZEXWebView?

    init(owner: ZEXWebView) { self.owner = owner }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        owner?.updateNavigationState()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        owner?.updateNavigationState()
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        owner?.updateNavigationState()
    }

    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard action.targetFrame?.isMainFrame != false,
              let url = action.request.url else {
            decisionHandler(.allow)
            return
        }

        let host = url.host?.lowercased() ?? ""
        let isZEX = host == "zxcstream.com" || host.hasSuffix(".zxcstream.com")
        guard url.scheme?.lowercased() == "https" else {
            decisionHandler(.cancel)
            return
        }
        if isZEX {
            decisionHandler(.allow)
        } else {
            decisionHandler(.cancel)
            UIApplication.shared.open(url)
        }
    }
}

private final class WebUIDelegate: NSObject, WKUIDelegate {
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        guard navigationAction.targetFrame == nil,
              let url = navigationAction.request.url,
              url.scheme?.lowercased() == "https" else { return nil }
        UIApplication.shared.open(url)
        return nil
    }
}
