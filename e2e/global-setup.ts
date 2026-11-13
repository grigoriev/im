import fs from 'node:fs';

// Waits until the application reports UP.
export default async function globalSetup(): Promise<void> {
  const base = process.env.BASE_URL ?? 'http://localhost:8080';
  const deadline = Date.now() + 120_000;
  for (;;) {
    try {
      const response = await fetch(`${base}/actuator/health`);
      if (response.ok && (await response.text()).includes('"UP"')) break;
    } catch {
      // not up yet
    }
    if (Date.now() > deadline) throw new Error(`${base} did not report UP within two minutes`);
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  // Each worker logs in as the admin on its own (tests/fixtures.ts). Old sessions of an earlier run go.
  fs.rmSync('.auth', { recursive: true, force: true });
  fs.mkdirSync('.auth', { recursive: true });
}
