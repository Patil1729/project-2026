import { useApp } from './AppContext';
import { permissionsFor } from '../lib/permissions';
export const usePermissions = () => permissionsFor(useApp().user);
