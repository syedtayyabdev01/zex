using System.ComponentModel;
using System.Diagnostics;
using System.Windows;
using System.Windows.Input;
using Microsoft.Web.WebView2.Core;

namespace Zex.Desktop;

public partial class MainWindow : Window
{
    private static readonly Uri HomeUri = new("https://zxcstream.com/");
    private WindowState _savedWindowState;
    private WindowStyle _savedWindowStyle;
    private ResizeMode _savedResizeMode;
    private bool _inWebFullscreen;

    public MainWindow()
    {
        InitializeComponent();
    }

    private async void Window_Loaded(object sender, RoutedEventArgs e)
    {
        try
        {
            await Browser.EnsureCoreWebView2Async();
            if (Browser.CoreWebView2 is null) return;

            var core = Browser.CoreWebView2;
            core.Settings.AreDefaultContextMenusEnabled = true;
            core.Settings.IsStatusBarEnabled = false;
            core.NavigationStarting += Core_NavigationStarting;
            core.NavigationCompleted += Core_NavigationCompleted;
            core.NewWindowRequested += Core_NewWindowRequested;
            core.ContainsFullScreenElementChanged += Core_ContainsFullScreenElementChanged;
            core.Navigate(HomeUri.AbsoluteUri);
        }
        catch (Exception ex) when (ex is WebView2RuntimeNotFoundException or InvalidOperationException)
        {
            ShowRuntimeError();
        }
    }

    private void Browser_InitializationCompleted(object? sender, CoreWebView2InitializationCompletedEventArgs e)
    {
        if (!e.IsSuccess) ShowRuntimeError();
    }

    private void Core_NavigationStarting(object? sender, CoreWebView2NavigationStartingEventArgs e)
    {
        if (!Uri.TryCreate(e.Uri, UriKind.Absolute, out var uri))
        {
            e.Cancel = true;
            return;
        }

        if (uri.Scheme.Equals(Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase) && IsZexHost(uri.Host)) return;
        e.Cancel = true;
        if (uri.Scheme.Equals(Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)) OpenExternal(uri);
    }

    private void Core_NewWindowRequested(object? sender, CoreWebView2NewWindowRequestedEventArgs e)
    {
        e.Handled = true;
        if (Uri.TryCreate(e.Uri, UriKind.Absolute, out var uri) &&
            uri.Scheme.Equals(Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)) OpenExternal(uri);
    }

    private async void Core_NavigationCompleted(object? sender, CoreWebView2NavigationCompletedEventArgs e)
    {
        UpdateNavigationButtons();
        if (Browser.CoreWebView2 is null) return;
        try { await Browser.CoreWebView2.ExecuteScriptAsync(SocialLinksScript); }
        catch (InvalidOperationException) { }
    }

    private static bool IsZexHost(string host) =>
        host.Equals("zxcstream.com", StringComparison.OrdinalIgnoreCase) ||
        host.EndsWith(".zxcstream.com", StringComparison.OrdinalIgnoreCase);

    private static void OpenExternal(Uri uri)
    {
        try { Process.Start(new ProcessStartInfo(uri.AbsoluteUri) { UseShellExecute = true }); }
        catch (Win32Exception) { }
    }

    private void Back_Click(object sender, RoutedEventArgs e)
    {
        if (Browser.CoreWebView2?.CanGoBack == true) Browser.CoreWebView2.GoBack();
    }

    private void Home_Click(object sender, RoutedEventArgs e) => Browser.CoreWebView2?.Navigate(HomeUri.AbsoluteUri);
    private void Reload_Click(object sender, RoutedEventArgs e) => Browser.CoreWebView2?.Reload();

    private void UpdateNavigationButtons()
    {
        BackButton.IsEnabled = Browser.CoreWebView2?.CanGoBack == true;
    }

    private void Window_PreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (Keyboard.Modifiers == ModifierKeys.Control && e.Key == Key.R)
        {
            Browser.CoreWebView2?.Reload();
            e.Handled = true;
        }
        else if (Keyboard.Modifiers == ModifierKeys.Control && e.Key == Key.H)
        {
            Browser.CoreWebView2?.Navigate(HomeUri.AbsoluteUri);
            e.Handled = true;
        }
        else if (Keyboard.Modifiers == ModifierKeys.Alt && e.Key == Key.Left)
        {
            if (Browser.CoreWebView2?.CanGoBack == true) Browser.CoreWebView2.GoBack();
            e.Handled = true;
        }
        else if (Keyboard.Modifiers == ModifierKeys.Alt && e.Key == Key.Right)
        {
            if (Browser.CoreWebView2?.CanGoForward == true) Browser.CoreWebView2.GoForward();
            e.Handled = true;
        }
        else if (e.Key == Key.Escape && _inWebFullscreen)
        {
            Browser.CoreWebView2?.ExecuteScriptAsync("document.exitFullscreen && document.exitFullscreen();");
            e.Handled = true;
        }
    }

    private void Core_ContainsFullScreenElementChanged(object? sender, object e)
    {
        Dispatcher.Invoke(() =>
        {
            var fullscreen = Browser.CoreWebView2?.ContainsFullScreenElement == true;
            if (fullscreen == _inWebFullscreen) return;

            if (fullscreen)
            {
                _savedWindowState = WindowState;
                _savedWindowStyle = WindowStyle;
                _savedResizeMode = ResizeMode;
                _inWebFullscreen = true;
                ToolbarRow.Height = new GridLength(0);
                Toolbar.Visibility = Visibility.Collapsed;
                WindowStyle = WindowStyle.None;
                ResizeMode = ResizeMode.NoResize;
                WindowState = WindowState.Maximized;
            }
            else
            {
                WindowStyle = _savedWindowStyle;
                ResizeMode = _savedResizeMode;
                WindowState = _savedWindowState;
                ToolbarRow.Height = GridLength.Auto;
                Toolbar.Visibility = Visibility.Visible;
                _inWebFullscreen = false;
            }
        });
    }

    private void ShowRuntimeError()
    {
        Browser.Visibility = Visibility.Collapsed;
        RuntimeErrorPanel.Visibility = Visibility.Visible;
    }

    private void InstallRuntime_Click(object sender, RoutedEventArgs e) =>
        OpenExternal(new Uri("https://developer.microsoft.com/microsoft-edge/webview2/"));

    private const string SocialLinksScript = """
        (() => {
          const patch = () => {
            const dialogs = [...document.querySelectorAll('[role="dialog"]')];
            const dialog = dialogs.find(node => (node.innerText || '').includes('Stay Connected'));
            if (dialog) {
              const links = [...dialog.querySelectorAll('a[href]')];
              const discord = links.find(a => /discord\.(com|gg)/i.test(a.href));
              const telegram = links.find(a => /(t\.me|telegram\.me)/i.test(a.href));
              const github = links.find(a => /facebook\.com/i.test(a.href));
              if (discord) { discord.href = 'https://discord.gg/UHq3x5QjH'; discord.setAttribute('aria-label', 'ZEX Discord server'); }
              if (telegram) { telegram.href = 'https://t.me/+MJvtkm6__Ag3ZWY0'; telegram.setAttribute('aria-label', 'ZEX Telegram channel'); }
              if (github) {
                github.href = 'https://github.com/syedtayyabdev01/zex';
                github.setAttribute('aria-label', 'ZEX GitHub repository');
                github.style.color = '#f5f5f7';
                const label = github.querySelector('span'); if (label) label.textContent = 'GitHub';
                const icon = github.querySelector('svg');
                if (icon) { icon.setAttribute('viewBox', '0 0 24 24'); icon.innerHTML = '<path fill="currentColor" d="M12 .8a11.2 11.2 0 0 0-3.54 21.83c.56.1.76-.24.76-.54v-1.95c-3.13.68-3.79-1.33-3.79-1.33-.5-1.3-1.25-1.65-1.25-1.65-1.02-.7.08-.69.08-.69 1.12.08 1.72 1.16 1.72 1.16 1 1.72 2.63 1.23 3.27.94.1-.71.39-1.2.71-1.48-2.5-.29-5.13-1.25-5.13-5.57 0-1.23.44-2.24 1.16-3.03-.12-.29-.5-1.44.11-3 0 0 .94-.3 3.07 1.15a10.6 10.6 0 0 1 5.6 0c2.12-1.45 3.07-1.15 3.07-1.15.61 1.56.22 2.71.11 3 .72.79 1.15 1.8 1.15 3.03 0 4.33-2.64 5.28-5.15 5.56.4.35.76 1.04.76 2.1v3.11c0 .3.2.65.77.54A11.2 11.2 0 0 0 12 .8z"/>'; }
              }
            }

            [...document.querySelectorAll('button')].forEach(button => {
              if ((button.innerText || '').trim().toLowerCase() !== 'install') return;
              const menuText = (button.parentElement?.innerText || '').toLowerCase();
              if (menuText.includes('library') && menuText.includes('settings')) button.style.display = 'none';
            });
          };
          const observer = new MutationObserver(patch);
          observer.observe(document.documentElement, { childList: true, subtree: true });
          patch();
          setTimeout(() => observer.disconnect(), 30000);
        })();
        """;
}
