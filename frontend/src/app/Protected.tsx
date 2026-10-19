import type {ReactNode} from 'react';
import {Link,Navigate,useLocation} from 'react-router-dom';
import {useStoreContext} from './providers';
import {Loading,ErrorState} from '../components/ui';
export function Protected({children}:{children:ReactNode}){const {user,userLoading,userError}=useStoreContext();const location=useLocation();if(userLoading)return <Loading/>;if(userError)return <div className="page"><ErrorState error={userError}/><Link to="/login">Sign in again</Link></div>;return user?<>{children}</>:<Navigate replace to={'/login?redirect='+encodeURIComponent(location.pathname)}/>}
