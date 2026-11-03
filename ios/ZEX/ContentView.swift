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
                    .foregroundColor(Color(red: 0.90, green: 0.04, blue: 0.08))
                    .padding(.horizontal, 8)

                Rectangle()
                    .fill(.white.opacity(0.18))
                    .frame(width: 1, height: 28)

                toolbarButton("chevron.left", label: "Back") { browser.goBack() }
                    .disabled(!browser.canGoBack)
                toolbarButton("house", label: "Home") { browser.load(homeURL) }
                toolbarButton("arrow.clockwise", label: "Reload") { browser.reload() }
                Spacer(minLength: 4)
                Text("ZEX · iOS 0.1.4")
                    .font(.system(size: 10, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.white.opacity(0.58))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 22)
                    .fill(LinearGradient(
                        gradient: Gradient(colors: [Color.white.opacity(0.25), Color.black.opacity(0.62)]),
                        startPoint: .top,
                        endPoint: .bottom
                    ))
                    .overlay(RoundedRectangle(cornerRadius: 22).stroke(Color.white.opacity(0.24), lineWidth: 1))
            )
            .padding(.horizontal, 14)
            .padding(.top, 8)
            .padding(.bottom, 8)
            .background(Color(red: 0.06, green: 0.06, blue: 0.07))

            BrowserView(browser: browser)
                .background(Color.black)
                .overlay(
                    Group {
                        if browser.isLoading {
                            VStack(spacing: 12) {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                                Text("Loading ZEX…").font(.system(size: 14)).foregroundColor(.white.opacity(0.8))
                            }
                            .padding(22)
                            .background(RoundedRectangle(cornerRadius: 18).fill(Color.black.opacity(0.78)))
                        } else if let error = browser.loadError {
                            VStack(spacing: 12) {
                                Text("ZEX page couldn’t load")
                                    .font(.system(size: 19, weight: .semibold))
                                    .foregroundColor(.white)
                                Text(error)
                                    .font(.system(size: 13))
                                    .foregroundColor(.white.opacity(0.72))
                                    .multilineTextAlignment(.center)
                                Button("Try again") { browser.load(homeURL) }
                                    .padding(.horizontal, 20)
                                    .padding(.vertical, 10)
                                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.14)))
                                    .foregroundColor(.white)
                            }
                            .padding(24)
                            .frame(maxWidth: 360)
                            .background(RoundedRectangle(cornerRadius: 20).fill(Color.black.opacity(0.84)))
                            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.18), lineWidth: 1))
                        }
                    }
                )
        }
        .background(Color.black.ignoresSafeArea())
    }

    private func toolbarButton(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 38, height: 36)
                .background(RoundedRectangle(cornerRadius: 13).fill(Color.white.opacity(0.08)))
                .overlay(RoundedRectangle(cornerRadius: 13).stroke(Color.white.opacity(0.14), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

@MainActor
final class ZEXWebView: ObservableObject {
    let view: WKWebView
    @Published private(set) var canGoBack = false
    @Published private(set) var isLoading = false
    @Published private(set) var loadError: String?
    private var navigationDelegate: WebNavigationDelegate!
    private var uiDelegate: WebUIDelegate!
    private var loadTimeout: DispatchWorkItem?

    init() {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.websiteDataStore = .default()
        view = WKWebView(frame: .zero, configuration: configuration)
        // Identify this embedded WebKit view as Safari so the site serves its
        // full experience instead of a reduced embedded-browser shell.
        let systemVersion = UIDevice.current.systemVersion
        let majorVersion = Int(systemVersion.split(separator: ".").first ?? "15") ?? 15
        let safariVersion = majorVersion >= 16 ? "605.1.15" : "604.1"
        let deviceDescription = UIDevice.current.userInterfaceIdiom == .pad
            ? "iPad; CPU OS \(systemVersion.replacingOccurrences(of: ".", with: "_"))"
            : "iPhone; CPU iPhone OS \(systemVersion.replacingOccurrences(of: ".", with: "_"))"
        view.customUserAgent = "Mozilla/5.0 (\(deviceDescription) like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/\(systemVersion) Mobile/15E148 Safari/\(safariVersion)"
        view.allowsBackForwardNavigationGestures = true
        view.isOpaque = false
        view.backgroundColor = .black
        view.scrollView.backgroundColor = .black
        navigationDelegate = WebNavigationDelegate(owner: self)
        uiDelegate = WebUIDelegate()
        view.navigationDelegate = navigationDelegate
        view.uiDelegate = uiDelegate
    }

    func load(_ url: URL) {
        beginNavigation()
        view.load(URLRequest(url: url))
    }

    func beginNavigation() {
        loadTimeout?.cancel()
        isLoading = true
        loadError = nil
        let timeout = DispatchWorkItem { [weak self] in
            guard let self, self.isLoading else { return }
            self.isLoading = false
            self.loadError = "The website did not respond in time. Check your connection and retry."
        }
        loadTimeout = timeout
        DispatchQueue.main.asyncAfter(deadline: .now() + 25, execute: timeout)
    }

    func reload() { load(view.url ?? homeURL) }
    func goBack() { if view.canGoBack { view.goBack() } }
    func updateNavigationState() { canGoBack = view.canGoBack }

    func finishNavigation(error: Error? = nil) {
        loadTimeout?.cancel()
        isLoading = false
        if let error = error as NSError?, error.code != NSURLErrorCancelled {
            loadError = error.localizedDescription
        }
        updateNavigationState()
    }
}

private struct BrowserView: UIViewRepresentable {
    let browser: ZEXWebView
    func makeUIView(context: Context) -> WKWebView {
        browser.load(homeURL)
        return browser.view
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

private final class WebNavigationDelegate: NSObject, WKNavigationDelegate {
    weak var owner: ZEXWebView?

    init(owner: ZEXWebView) { self.owner = owner }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        owner?.finishNavigation()
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        owner?.beginNavigation()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        owner?.finishNavigation(error: error)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        owner?.finishNavigation(error: error)
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
