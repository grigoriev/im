// Passkeys: registration on "Mein Konto" and login on the login page, through the WebAuthn endpoints
// of Spring Security. Messages are German; the page shows them in the element [data-passkey-message].
(() => {
  const b64 = {
    encode(buffer) {
      return btoa(String.fromCodePoint(...new Uint8Array(buffer))).replaceAll('=', '').replaceAll('+', '-').replaceAll('/', '_');
    },
    decode(text) {
      const binary = atob(text.replaceAll('-', '+').replaceAll('_', '/'));
      return Uint8Array.from(binary, (char) => char.codePointAt(0)).buffer;
    },
  };

  const csrfHeaders = () => {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    return token && header ? { [header.content]: token.content } : {};
  };

  const post = async (url, body) => {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...csrfHeaders() },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
  };

  const show = (text) => {
    const element = document.querySelector('[data-passkey-message]');
    if (element) {
      element.textContent = text;
      element.hidden = false;
    }
  };

  const explain = (error) =>
    error?.name === 'NotAllowedError'
      ? 'Der Vorgang wurde abgebrochen oder ist abgelaufen.'
      : 'Das hat nicht geklappt. Bitte versuchen Sie es noch einmal.';

  async function login() {
    const options = await post('/webauthn/authenticate/options');
    const credential = await navigator.credentials.get({
      publicKey: {
        ...options,
        challenge: b64.decode(options.challenge),
        allowCredentials: (options.allowCredentials ?? []).map((item) => ({ ...item, id: b64.decode(item.id) })),
      },
    });
    const { response } = credential;
    const result = await post('/login/webauthn', {
      id: credential.id,
      rawId: b64.encode(credential.rawId),
      response: {
        authenticatorData: b64.encode(response.authenticatorData),
        clientDataJSON: b64.encode(response.clientDataJSON),
        signature: b64.encode(response.signature),
        userHandle: response.userHandle ? b64.encode(response.userHandle) : undefined,
      },
      credType: credential.type,
      clientExtensionResults: credential.getClientExtensionResults(),
      authenticatorAttachment: credential.authenticatorAttachment,
    });
    if (!result.authenticated) throw new Error('not authenticated');
    globalThis.location.href = result.redirectUrl;
  }

  async function register(label) {
    const options = await post('/webauthn/register/options');
    const credential = await navigator.credentials.create({
      publicKey: {
        ...options,
        user: { ...options.user, id: b64.decode(options.user.id) },
        challenge: b64.decode(options.challenge),
        excludeCredentials: (options.excludeCredentials ?? []).map((item) => ({ ...item, id: b64.decode(item.id) })),
      },
    });
    const { response } = credential;
    const result = await post('/webauthn/register', {
      publicKey: {
        label,
        credential: {
          id: credential.id,
          rawId: b64.encode(credential.rawId),
          response: {
            attestationObject: b64.encode(response.attestationObject),
            clientDataJSON: b64.encode(response.clientDataJSON),
            transports: response.getTransports ? response.getTransports() : [],
          },
          type: credential.type,
          clientExtensionResults: credential.getClientExtensionResults(),
          authenticatorAttachment: credential.authenticatorAttachment,
        },
      },
    });
    if (!result.success) throw new Error('registration refused');
    globalThis.location.href = '/konto?passkey=neu';
  }

  document.addEventListener('DOMContentLoaded', () => {
    const supported = Boolean(globalThis.PublicKeyCredential);
    document.querySelectorAll('[data-passkey-login]').forEach((button) => {
      if (!supported) {
        button.disabled = true;
        return;
      }
      button.addEventListener('click', () => login().catch((error) => show(explain(error))));
    });
    document.querySelectorAll('form[data-passkey-register]').forEach((form) => {
      form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (!supported) {
          show('Dieser Browser unterstützt keine Passkeys.');
          return;
        }
        register(new FormData(form).get('label')).catch((error) => show(explain(error)));
      });
    });
  });
})();
