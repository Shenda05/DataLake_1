import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { login as requestLogin, type LoginResult } from '../api/platform';

type UserProfile = Pick<LoginResult, 'username' | 'role' | 'displayName' | 'menus'>;

const TOKEN_KEY = 'data-lake-auth-token';
const PROFILE_KEY = 'data-lake-auth-profile';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '');
  const profile = ref<UserProfile | null>(
    (() => {
      const raw = localStorage.getItem(PROFILE_KEY);
      return raw ? (JSON.parse(raw) as UserProfile) : null;
    })()
  );

  const isLoggedIn = computed(() => Boolean(token.value && profile.value));
  const allowedMenus = computed(() => profile.value?.menus || []);

  async function login(username: string, password: string) {
    const result = await loginApi({ username, password });
    token.value = result.token;
    profile.value = {
      username: result.username,
      role: result.role,
      displayName: result.displayName,
      menus: result.menus
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
