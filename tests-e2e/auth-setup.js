import { test as setup } from '@playwright/test';
import fs from 'fs';

setup('authenticate', async ({ context }) => {
  // REPLACE THIS with the cookie value you copied
  const SESSION_VALUE = 'Y2ZjNzRiYWQtZmI2My00NzNiLTk1ZTgtNjM4ZWYyY2FlODI3';

  await context.addCookies([{
    name: 'SESSION',
    value: SESSION_VALUE,
    domain: 'localhost',
    path: '/',
    httpOnly: true,
    secure: false,
    sameSite: 'Lax'
  }]);

  // Save the state to a file
  await context.storageState({ path: 'tests-e2e/userStorageState.json' });
});