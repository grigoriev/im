import type { Page } from '@playwright/test';

// A virtual platform authenticator of Chromium, like Touch ID: it holds discoverable passkeys and
// confirms the user without a prompt.
export async function addVirtualAuthenticator(page: Page): Promise<() => Promise<number>> {
  const client = await page.context().newCDPSession(page);
  await client.send('WebAuthn.enable');
  const { authenticatorId } = await client.send('WebAuthn.addVirtualAuthenticator', {
    options: {
      protocol: 'ctap2',
      transport: 'internal',
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true,
    },
  });
  return async () => (await client.send('WebAuthn.getCredentials', { authenticatorId })).credentials.length;
}
