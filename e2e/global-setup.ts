// Waits until the application reports UP, at most two minutes.
export default async function globalSetup(): Promise<void> {
  const base = process.env.BASE_URL ?? 'http://web:8080';
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${base}/actuator/health`);
      if (response.ok && (await response.text()).includes('"UP"')) return;
    } catch {
      // not up yet
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error(`${base} did not report UP within two minutes`);
}
