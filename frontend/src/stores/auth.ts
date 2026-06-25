import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { getAuthProfile, login as requestLogin, type LoginResult } from '../api/platform';

type UserProfile = Pick<LoginResult, 'username' | 'role' | 'displayName' | 'menus' | 'actions'>;

const TOKEN_KEY = 'data-lake-auth-token';
const PROFILE_KEY = 'data-lake-auth-profile';
const PROFILE_SYNC_INTERVAL_MS = 15000;

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '');
  const profile = ref<UserProfile | null>(readStoredProfile());
  const lastProfileSyncAt = ref(0);

  const isLoggedIn = computed(() => Boolean(token.value && profile.value));
  const allowedMenus = computed(() => profile.value?.menus ?? []);
  const allowedActions = computed(() => profile.value?.actions ?? []);

  async function login(username: string, password: string) {
    const result = await loginApi({ username, password });
    token.value = result.token;
    applyProfile(result);
    localStorage.setItem(TOKEN_KEY, token.value);
  }

  function logout() {
    token.value = '';
    profile.value = null;
    lastProfileSyncAt.value = 0;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(PROFILE_KEY);
  }

  async function syncProfile(force = false) {
    if (!token.value || !profile.value) {
      return;
    }
    if (!force && Date.now() - lastProfileSyncAt.value < PROFILE_SYNC_INTERVAL_MS) {
      return;
    }
    const result = await getAuthProfile();
    applyProfile({
      token: token.value,
      username: result.username,
      role: result.role,
      displayName: result.displayName,
      menus: Array.isArray(result.menus) ? result.menus : [],
      actions: Array.isArray(result.actions) ? result.actions : []
    });
  }

  function hasAction(action: string) {
    return allowedActions.value.includes(action as never);
  }

  function applyProfile(result: LoginResult) {
    profile.value = {
      username: result.username,
      role: result.role,
      displayName: result.displayName,
      menus: Array.isArray(result.menus) ? result.menus : [],
      actions: Array.isArray(result.actions) ? result.actions : []
    };
    lastProfileSyncAt.value = Date.now();
    localStorage.setItem(PROFILE_KEY, JSON.stringify(profile.value));
  }

  return {
    token,
    profile,
    isLoggedIn,
    allowedMenus,
    allowedActions,
    lastProfileSyncAt,
    login,
    syncProfile,
    hasAction,
    logout
  };
});

async function loginApi(payload: { username: string; password: string }) {
  return requestLogin(payload);
}

function readStoredProfile(): UserProfile | null {
  const raw = localStorage.getItem(PROFILE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<UserProfile>;
    if (!parsed.username || !parsed.role || !parsed.displayName) {
      throw new Error('stored profile is incomplete');
    }
    return {
      username: parsed.username,
      role: parsed.role,
      displayName: parsed.displayName,
      menus: Array.isArray(parsed.menus) ? parsed.menus : [],
      actions: Array.isArray(parsed.actions) ? parsed.actions : []
    };
  } catch {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(PROFILE_KEY);
    return null;
  }
}
