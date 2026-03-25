/**
 * Minimal typings for Google Identity Services loaded from https://accounts.google.com/gsi/client
 *
 * OAuth note: Authorized JavaScript origins in Google Cloud must match window.location.origin
 * exactly (scheme + host + port, no path, no trailing slash). Any mismatch yields origin_mismatch.
 * If issues persist: clear site data for accounts.google.com, try incognito, confirm you are not on
 * 127.0.0.1 when only localhost is registered (or vice versa).
 */
export interface GoogleCredentialResponse {
  credential?: string;
  select_by?: string;
}

export interface GoogleIdInitializeConfig {
  client_id: string;
  callback: (response: GoogleCredentialResponse) => void;
  auto_select?: boolean;
  cancel_on_tap_outside?: boolean;
  /** Set false if FedCM causes origin or prompt issues in some environments. */
  use_fedcm_for_prompt?: boolean;
}

export interface GoogleIdRenderButtonConfig {
  type?: 'standard' | 'icon';
  theme?: 'outline' | 'filled_blue' | 'filled_black';
  size?: 'large' | 'medium' | 'small';
  text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin';
  shape?: 'rectangular' | 'pill' | 'circle' | 'square';
  logo_alignment?: 'left' | 'center';
  width?: string | number;
  locale?: string;
}

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: GoogleIdInitializeConfig) => void;
          renderButton: (parent: HTMLElement, options: GoogleIdRenderButtonConfig) => void;
          disableAutoSelect: () => void;
          prompt: (momentListener?: (notification: unknown) => void) => void;
        };
      };
    };
  }
}

export {};
