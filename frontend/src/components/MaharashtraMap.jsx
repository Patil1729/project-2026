import { useRegisteredUsers } from '../features/dashboard/RegisteredUsersContext';
import { groupUserLocations, canonicalDistrict } from '../lib/userLocations';
import points from '../assets/district-points.json';
import { number } from '../lib/format';
import { t, useLocale } from "../lib/i18n";
import { useState } from 'react';
import { Plus, Minus, RotateCcw, MapPin } from 'lucide-react';
import geography from '../assets/maharashtra.json';
import { Panel, IconButton } from './ui';
// Locally bundled Census 2011 district geometry. Not current administrative boundaries.
const project = ([lon, lat]) => [40 + (lon - 72.6) * 78, 560 - (lat - 15.6) * 83];
function polygonPath(geometry) {
  const polygons = geometry.type === 'MultiPolygon' ? geometry.coordinates : [geometry.coordinates];
  return polygons.map(rings => rings.map(ring => ring.map((p, i) => `${i ? 'L' : 'M'}${project(p).map(n => n.toFixed(1)).join(',')}`).join('') + 'Z').join('')).join('');
}
const districts = geography.features.map(f => ({
  name: f.properties.district,
  path: polygonPath(f.geometry)
}));
const cities = [['Mumbai', 72.88, 19.08], ['Pune', 73.86, 18.52], ['Nagpur', 79.09, 21.15], ['Nashik', 73.79, 20], ['Kolhapur', 74.24, 16.70], ['Chhatrapati Sambhajinagar', 75.34, 19.88]];
const districtPoints=Object.fromEntries(Object.entries(points).map(([key,p])=>[key,{...p,key:'district:'+key,district:key}]));
export default function MaharashtraMap({userData} = {}) {
  useLocale();
  const resource=useRegisteredUsers();
  const data=userData===undefined?resource.data:userData;
  const mapped=data&&!data.locationsLoading?groupUserLocations(data.users,data.profiles,districtPoints):{groups:[],unmapped:0};
  const [zoom, setZoom] = useState(1),
    [selected, setSelected] = useState('');
  return <Panel title={t("Explore Maharashtra")} subtitle={t("A clear geographic view of Maharashtra and its districts.")} className="maharashtra-panel" action={<div className="map-tools"><IconButton icon={Minus} label={t("Zoom out")} disabled={zoom <= 1} onClick={() => setZoom(z => Math.max(1, z - .25))} /><IconButton icon={Plus} label={t("Zoom in")} disabled={zoom >= 2.5} onClick={() => setZoom(z => Math.min(2.5, z + .25))} /><IconButton icon={RotateCcw} label={t("Reset map")} onClick={() => {
      setZoom(1);
      setSelected('');
    }} /></div>}>
    <div className="map-layout"><div className="map-scroll" tabIndex={0} aria-label={t("Maharashtra state map")}>
      <svg viewBox="0 0 700 600" style={{
          width: `${zoom * 100}%`, maxHeight: zoom === 1 ? "min(510px, 70vw)" : "none"
        }} role="group" aria-label={t("Maharashtra state map")}>
        <title>{t("Maharashtra state map")}</title>
        <rect width="700" height="600" rx="18" fill="#f4f8f5" />
        <g fill="#ffdbac" stroke="#ffffff" strokeWidth="1" strokeLinejoin="round">
          {districts.map(d => <path key={d.name} d={d.path} fill={selected === d.name ? '#ec7c22' : undefined} tabIndex={0} role="button" aria-pressed={selected === d.name} aria-label={t(d.name)} onClick={() => setSelected(d.name)} onKeyDown={e => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                setSelected(d.name);
              }
            }}><title>{t(d.name)}</title></path>)}
        </g>
        {cities.map(([name, lon, lat]) => {
            const [x, y] = project([lon, lat]);
            return <g key={name} pointerEvents="none"><circle cx={x} cy={y} r="3.5" fill="#89969c" stroke="white" strokeWidth="1.5" /><text x={x + 7} y={y + 4} fontSize={name.length > 15 ? 10 : 12} fill="#173e35" fontWeight="650">{t(name)}</text></g>;
          })}
        {mapped.groups.map(group=>{const [x,y]=project([group.lon,group.lat]);return <g key={group.key} className="active-user-marker" data-location={group.name} data-count={group.count} data-precision={group.precision} role="button" tabIndex={0} aria-label={`${t(group.name)}: ${number(group.count)} ${t('active registered users')}`} onClick={()=>setSelected(districts.find(d=>canonicalDistrict(d.name)===group.district)?.name||'')} onKeyDown={e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();setSelected(districts.find(d=>canonicalDistrict(d.name)===group.district)?.name||'');}}}><title>{`${t(group.name)} · ${t('Active registered users')}: ${number(group.count)} · ${t(group.precision==='city'?'Approximate city location':'Approximate district location')}`}</title><circle cx={x} cy={y} r="13" fill="#16a34a" opacity=".15"/><circle cx={x} cy={y} r={group.count>1?9:6} fill="#16a34a" stroke="white" strokeWidth="2"/>{group.count>1&&<text x={x} y={y+3.5} textAnchor="middle" fontSize="10" fontWeight="700" fill="white" pointerEvents="none">{number(group.count)}</text>}</g>})}
        <text x="644" y="36" fill="#526a60" fontSize="13">N ↑</text>
      </svg>
    </div><div className="map-details"><MapPin size={28} /><h3>{t(selected || 'Maharashtra')}</h3><p>{t("Select a district to explore.")}</p>
      <select className="input" aria-label={t("District")} value={selected} onChange={e => setSelected(e.target.value)}><option value="">{t("Maharashtra")}</option>{districts.map(d => <option key={d.name} value={d.name}>{t(d.name)}</option>)}</select>
      {data?<div className="user-map-legend"><p><span className="active-user-dot"/>{t('Green dots show enabled registered users, not online presence.')}</p>{data.locationsLoading?<p role="status">{t('Loading user locations…')}</p>:<><strong>{number(mapped.groups.filter(g=>!selected||g.district===canonicalDistrict(selected)).reduce((sum,g)=>sum+g.count,0))} {t('mapped active users')}</strong><p>{number(mapped.unmapped)} {t('active users without a mapped location')}</p>{!!data.failures?.length&&<p role="status">{t('Some user locations could not be loaded.')}</p>}</>}<p>{t('Locations use saved profile city or district centres. Multiple users at one location are grouped.')}</p></div>:<p>{t(resource.error?'User data could not be loaded.':'Geographic reference only; no membership totals are inferred.')}</p>}<small>{t("District boundaries: Census 2011 \xB7 ")}<a href="https://github.com/udit-001/india-maps-data" target="_blank" rel="noopener noreferrer">{t("India Maps Data")}</a></small>
    </div></div>
  </Panel>;
}
