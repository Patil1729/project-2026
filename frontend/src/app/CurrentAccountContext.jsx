import { createContext, useContext } from 'react';
import { useApp, useResource } from './AppContext';
const Account = createContext({ data:null, loading:false, error:null });
export const useCurrentAccount = () => useContext(Account);
export function CurrentAccountProvider({ children }) {
  const { user } = useApp();
  const resource = useResource(`/users/${user?.id || 0}`, {}, !!user);
  return <Account.Provider value={resource}>{children}</Account.Provider>;
}
