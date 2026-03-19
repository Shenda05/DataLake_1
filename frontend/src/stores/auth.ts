import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { login as loginApi, type LoginResult } from '../api/platform';

const TOKEN_KEY = 'data-lake-auth-token';
const PROFILE_KEY = 'data-lake-auth-profile';
const MENUS_KEY = 'data-lake-auth-menus';

export type UserProfile = {
  username: string;
  role: 'ADMIN' | 'OPERATOR';
  displayName: string;
};

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '');
  const profile = ref<UserProfile | null>(
    (() => {
      const raw = localStorage.getItem(PROFILE_KEY);
      return raw ? (JSON.parse(raw) as UserProfile) : null;
    })()
  );
  const menus = ref<string[]>(
    (() => {
      const raw = localStorage.getItem(MENUS_KEY);
      return raw ? (JSON.parse(raw) as string[]) : [];
    })()
  );

  const isLoggedIn = computed(() => Boolean(token.value && profile.value));
  const allowedMenus = computed(() => menus.value);

  async function login(username: string, password: string) {
    const user = await loginApi({ username, password });
    token.value = user.token;
    profile.value = {
      username: user.username,
      role: user.role,
      displayName: user.displayName
    };
    menus.value = user.menus;
    localStorage.setItem(TOKEN_KEY, token.value);
    localStorage.setItem(PROFILE_KEY, JSON.stringify(profile.value));
    localStorage.setItem(MENUS_KEY, JSON.stringify(menus.value));
  }

  function logout() {
    token.value = '';
    profile.value = null;
    menus.value = [];
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(PROFILE_KEY);
    localStorage.removeItem(MENUS_KEY);
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
