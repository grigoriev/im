// The admin that docker-compose.yml creates at startup.
export const ADMIN = { username: 'admin', password: 'e2e-admin-passwort', displayName: 'Administrator' };

// No session: for the tests of the login page.
export const ANONYMOUS = { cookies: [], origins: [] };

// A unique login name, so parallel tests never collide.
export function uniqueUsername(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
}
