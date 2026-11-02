export const authPaths = {
  signup: import.meta.env?.VITE_AUTH_SIGNUP_PATH || "/auth/signup",
  csrf: import.meta.env?.VITE_AUTH_CSRF_PATH || '/auth/csrf',
  login: import.meta.env?.VITE_AUTH_LOGIN_PATH || '/auth/signin',
  user: import.meta.env?.VITE_AUTH_USER_PATH || '/auth/user',
  logout: import.meta.env?.VITE_AUTH_LOGOUT_PATH || '/auth/signout',
  changePassword: import.meta.env?.VITE_AUTH_CHANGE_PASSWORD_PATH || '/auth/change-password',
};
