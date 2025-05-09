import { useAuthStore } from '../stores/authStore';
import { signInWithKakao, signOut } from '../services/authService';

export const useAuth = () => {
  const { accessToken } = useAuthStore();
  const loggedIn = !!accessToken;

  return {
    loggedIn,
    signInWithKakao,
    signOut,
  };
};
