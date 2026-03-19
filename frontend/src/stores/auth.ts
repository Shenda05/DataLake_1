import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { login as requestLogin, type LoginResult } from '../api/platform';

type UserProfile = Pick<LoginResult, 'username' | 'role' | 'displayName' | 'menus'>;

const TOKEN_KEY = 'data-lake-auth-token';
const PROFILE_KEY = 'data-lake-auth-profile';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '');
  const profile = ref<UserProfile | null>(readStoredProfile());

  const isLoggedIn = computed(() => Boolean(token.value && profile.value));
  const allowedMenus = computed(() => profile.value?.menus ?? []);

  async function login(username: string, password: string) {
    const result = await loginApi({ username, password });
    token.value = result.token;
    profile.value = {
      username: result.username,
      role: result.role,
      displayName: result.displayName,
      menus: Array.isArray(result.menus) ? result.menus : []
    };
    localStorage.setItem(TOKEN_KEY, token.value);
    localStorage.setItem(PROFILE_KEY, JSON.stringify(profile.value));
  }

  function logout() {
    token.value = '';
    profile.value = null;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(PROFILE_KEY);
  }

  return {
    token,
    profile,
    isLoggedIn,
    allowedMenus,
    login,
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
      menus: Array.isArray(parsed.menus) ? parsed.menus : []
    };
  } catch {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(PROFILE_KEY);
    return null;
  }
}
