const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests-e2e',
  fullyParallel: false,
  reporter: 'html',
  // Increase global timeout for local rendering (30 seconds)
  timeout: 30000,

  use: {
    baseURL: 'http://localhost:8080',
    trace: 'on-first-retry',
    viewport: { width: 1440, height: 900 },
    actionTimeout: 10000,
  },

  projects: [
    {
      name: 'setup',
      testMatch: 'auth-setup.js',
    },
    {
        name: 'chromium',
        testMatch: '**/*.spec.js',
        use: {
          ...devices['Desktop Chrome'],
          storageState: 'tests-e2e/userStorageState.json',
        },
        dependencies: ['setup'],
      },
  ],
});