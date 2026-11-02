import {createContext,useContext,useEffect,useState} from 'react';
import {useApp} from '../../app/AppContext';
import {items} from '../../lib/format';
import {loadActiveProfiles} from '../../lib/userLocations';
const Context=createContext({data:null,loading:false,error:null});
export const useRegisteredUsers=()=>useContext(Context);
export function RegisteredUsersProvider({enabled,children}) {
 const {request,revision}=useApp();
 const [resource,setResource]=useState({data:null,loading:true,error:null});
 useEffect(()=>{
  if(!enabled){setResource({data:null,loading:false,error:null});return;}
  const controller=new AbortController();setResource({data:null,loading:true,error:null});
  (async()=>{try{
   const response=await request('/users',{signal:controller.signal});
   if(!Array.isArray(response))throw new Error('Expected the complete registered-user list.');
   const users=items(response);
   if(controller.signal.aborted)return;
   setResource({data:{users,profiles:{},failures:[],locationsLoading:true},loading:false,error:null});
   const {profiles,failures}=await loadActiveProfiles(users,request,controller.signal);
   if(!controller.signal.aborted)setResource({data:{users,profiles,failures,locationsLoading:false},loading:false,error:null});
  }catch(error){if(!controller.signal.aborted)setResource({data:null,loading:false,error});}})();
  return ()=>controller.abort();
 },[enabled,request,revision]);
 return <Context.Provider value={enabled?resource:{data:null,loading:false,error:null}}>{children}</Context.Provider>;
}
