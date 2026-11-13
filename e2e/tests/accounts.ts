import { randomUUID } from 'node:crypto';

// The admin that docker-compose.yml creates at startup.
export const ADMIN = { username: 'admin', password: 'e2e-admin-passwort', displayName: 'Administrator' };

// No session: for the tests of the login page.
export const ANONYMOUS = { cookies: [], origins: [] };

// A unique login name, so parallel tests never collide. A UUID, not Math.random: CodeQL treats a
// login name as security relevant.
export function uniqueUsername(prefix: string): string {
  return `${prefix}-${randomUUID().slice(0, 12)}`;
}
