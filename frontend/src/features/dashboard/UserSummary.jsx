import {useRegisteredUsers} from './RegisteredUsersContext';
import {userStatistics} from '../../lib/userLocations';
import {t,useLocale} from '../../lib/i18n';
import {number} from '../../lib/format';
import {Panel,ResourceState} from '../../components/ui';
export default function UserSummary(){
 useLocale();const resource=useRegisteredUsers();const stats=userStatistics(resource.data?.users||[]);
 return <ResourceState resource={resource}><div className="registered-user-stats">{[['Registered users',stats.registered,'registered'],['Active registered users',stats.active,'active'],['Inactive registered users',stats.inactive,'inactive']].map(([label,value,key])=><Panel key={key} title={t(label)}><strong className="registered-user-count" data-user-stat={key}>{number(value)}</strong></Panel>)}</div></ResourceState>;
}
