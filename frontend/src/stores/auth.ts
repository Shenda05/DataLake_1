import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { demoUsers, menuPermissions, mockLogin, type UserProfile } from '../mock/api';

const TOKEN_KEY = 'data-lake-demo-token';
const PROFILE_KEY = 'data-lake-demo-profile';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '');
  const profile = ref<UserProfile | null>(
    (() => {
      const raw = localStorage.getItem(PROFILE_KEY);
      return raw ? (JSON.parse(raw) as UserProfile) : null;
    })()
  );

  const isLoggedIn = computed(() => Boolean(token.value && profile.value));
  const allowedMenus = computed(() => {
    if (!profile.value) return [];
    return [...menuPermissions[profile.value.role]];
  });

  async function login(username: string, password: string) {
    const user = await mockLogin(username, password);
    token.value = `mock-token-${user.username}`;
    profile.value = user;
    localStorage.setItem(TOKEN_KEY, token.value);
    localStorage.setItem(PROFILE_KEY, JSON.stringify(user));
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
    demoAccounts: demoUsers,
    login,
    logout
  };
});

