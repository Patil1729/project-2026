import { createContext, useContext } from 'react';
import { useApp, useResource } from '../../app/AppContext';
const Community = createContext({ data: null, loading: false, error: null });
export const useCommunityAccess = () => useContext(Community);
export function CommunityAccessProvider({ children }) {
  const { user } = useApp();
  const resource = useResource('/community/access', {}, !!user);
  const data = user && Number(resource.data?.userId) === Number(user.id) ? resource.data : null;
  return <Community.Provider value={{ ...resource, data }}>{children}</Community.Provider>;
}
