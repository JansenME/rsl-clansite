class KloepieBotLauncher {
    constructor(options = {}) {
        this.protocol = 'kloepiebot://launch';
        this.downloadUrl = options.downloadUrl || '/api/download/installer';
        this.timeout = options.timeout || 3000;
        this.token = null;
    }

    /**
     * Main entry point: Detect app or trigger download flow
     */
    async launch(token) {
        this.token = token;
        const launchUrl = `${this.protocol}?token=${encodeURIComponent(token)}`;

        // Show attempting modal
        this.showModal('launching');

        // Try to launch app
        const appOpened = await this.attemptProtocolLaunch(launchUrl);

        if (appOpened) {
            // App launched - let it handle version checking internally
            this.closeModal();
            console.log('KloepieBot launched successfully');
            return;
        }

        // App not installed - show download flow
        this.showDownloadFlow();
    }

    /**
     * Attempt to launch protocol and detect if app opens
     */
    attemptProtocolLaunch(url) {
        return new Promise((resolve) => {
            let blurred = false;
            let resolved = false;

            // Detection method 1: Page visibility (most reliable)
            const handleVisibility = () => {
                if (document.hidden && !resolved) {
                    blurred = true;
                    resolved = true;
                    cleanup();
                    resolve(true);
                }
            };

            // Detection method 2: Window blur
            const handleBlur = () => {
                if (!resolved) {
                    blurred = true;
                    resolved = true;
                    cleanup();
                    resolve(true);
                }
            };

            // Cleanup listeners
            const cleanup = () => {
                document.removeEventListener('visibilitychange', handleVisibility);
                window.removeEventListener('blur', handleBlur);
            };

            // Setup listeners
            document.addEventListener('visibilitychange', handleVisibility);
            window.addEventListener('blur', handleBlur);

            // Launch protocol using hidden iframe (works across browsers)
            this.launchViaIframe(url);

            // Timeout - if no blur/visibility change, assume not installed
            setTimeout(() => {
                if (!resolved) {
                    resolved = true;
                    cleanup();
                    resolve(false);
                }
            }, this.timeout);
        });
    }

    launchViaIframe(url) {
        const iframe = document.createElement('iframe');
        iframe.style.display = 'none';
        iframe.src = url;
        document.body.appendChild(iframe);
        setTimeout(() => iframe.remove(), 1000);
    }

    /**
     * Show download and installation UI
     */
    showDownloadFlow() {
        this.showModal('download', {
            onDownload: () => this.startDownload(),
            onRetry: () => this.retryLaunch()
        });
    }

    startDownload() {
        // Trigger download
        window.location.href = this.downloadUrl;

        // Show installation instructions
        this.showModal('installing', {
            onLaunch: () => this.retryLaunch()
        });
    }

    retryLaunch() {
        this.closeModal();
        // Try again - if installation completed, app should now open
        setTimeout(() => this.launch(this.token), 500);
    }

    /**
     * Modal UI Management
     */
    showModal(type, callbacks = {}) {
        // Remove existing modal
        this.closeModal();

        const modal = document.createElement('div');
        modal.id = 'kloepiebot-modal';
        modal.style.cssText = `
            position: fixed; top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(0,0,0,0.85); z-index: 9999; display: flex;
            justify-content: center; align-items: center; font-family: system-ui, -apple-system, sans-serif;
        `;

        let content = '';

        switch(type) {
            case 'launching':
                content = `
                    <div style="background: #1a1a2e; color: white; padding: 40px; border-radius: 12px; text-align: center; max-width: 400px; border: 1px solid #16213e;">
                        <div style="width: 50px; height: 50px; border: 4px solid #333; border-top: 4px solid #e94560; border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 20px;"></div>
                        <h2 style="margin: 0 0 10px 0;">Opening KloepieBot...</h2>
                        <p style="color: #888; margin: 0;">Connecting to desktop app</p>
                        <style>@keyframes spin { 100% { transform: rotate(360deg); } }</style>
                    </div>
                `;
                break;

            case 'download':
                content = `
                    <div style="background: #1a1a2e; color: white; padding: 40px; border-radius: 12px; text-align: center; max-width: 450px; border: 1px solid #e94560;">
                        <div style="font-size: 48px; margin-bottom: 20px;">💻</div>
                        <h2 style="margin: 0 0 15px 0; color: #e94560;">KloepieBot Required</h2>
                        <p style="color: #ccc; margin-bottom: 25px; line-height: 1.5;">
                            The desktop app is needed to sync your champions.<br>
                            <small style="color: #666;">Windows 10/11 • ~5MB download</small>
                        </p>
                        <button id="kb-download-btn" style="background: #e94560; color: white; border: none; padding: 15px 30px; border-radius: 6px; font-size: 16px; cursor: pointer; font-weight: bold; width: 100%; margin-bottom: 10px;">
                            Download Installer
                        </button>
                        <button id="kb-retry-btn" style="background: transparent; color: #888; border: 1px solid #555; padding: 10px; border-radius: 6px; cursor: pointer; width: 100%;">
                            Already installed? Try again
                        </button>
                    </div>
                `;
                break;

            case 'installing':
                content = `
                    <div style="background: #1a1a2e; color: white; padding: 40px; border-radius: 12px; text-align: center; max-width: 450px; border: 1px solid #0f3460;">
                        <div style="font-size: 48px; margin-bottom: 20px;">⚙️</div>
                        <h2 style="margin: 0 0 15px 0;">Complete Installation</h2>
                        <div style="text-align: left; background: #0f0f1e; padding: 20px; border-radius: 8px; margin-bottom: 20px; color: #aaa; font-size: 14px; line-height: 1.6;">
                            <p style="margin: 0 0 10px 0;"><strong style="color: #fff;">1.</strong> Open <strong>KloepieBotSetup.msi</strong> from your downloads</p>
                            <p style="margin: 0 0 10px 0;"><strong style="color: #fff;">2.</strong> Follow the installation wizard (click "More info" → "Run anyway" if Windows warns)</p>
                            <p style="margin: 0;"><strong style="color: #fff;">3.</strong> Click Launch below when done</p>
                        </div>
                        <button id="kb-launch-btn" style="background: #0f3460; color: white; border: 2px solid #e94560; padding: 15px 30px; border-radius: 6px; font-size: 16px; cursor: pointer; font-weight: bold; width: 100%;">
                            🚀 Launch KloepieBot
                        </button>
                    </div>
                `;
                break;
        }

        modal.innerHTML = content;
        document.body.appendChild(modal);

        // Attach event listeners
        if (callbacks.onDownload) {
            document.getElementById('kb-download-btn')?.addEventListener('click', callbacks.onDownload);
        }
        if (callbacks.onRetry) {
            document.getElementById('kb-retry-btn')?.addEventListener('click', callbacks.onRetry);
        }
        if (callbacks.onLaunch) {
            document.getElementById('kb-launch-btn')?.addEventListener('click', callbacks.onLaunch);
        }
    }

    closeModal() {
        const existing = document.getElementById('kloepiebot-modal');
        if (existing) existing.remove();
    }
}

// Global instance
const kloepieBot = new KloepieBotLauncher();