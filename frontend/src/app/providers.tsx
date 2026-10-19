import {createContext,useContext,useState,useEffect,type ReactNode} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {api} from '../services';
import type {User} from '../types';
const Context=createContext<ReturnType<typeof useStore>|null>(null);
function useStore(){const qc=useQueryClient();const [toast,setToast]=useState('');const user=useQuery({queryKey:['user'],queryFn:api.getMe,retry:false});const cart=useQuery({queryKey:['cart'],queryFn:api.getCart,retry:false});
useEffect(()=>{if(toast){const t=setTimeout(()=>setToast(''),4000);return()=>clearTimeout(t)}},[toast]);
useEffect(()=>{const expire=()=>{qc.setQueryData(['user'],null);setToast('Your session has expired. Please sign in again.')};window.addEventListener('session-expired',expire);return()=>window.removeEventListener('session-expired',expire)},[qc]);
const add=useMutation({mutationFn:({id,quantity}:{id:string;quantity:number})=>api.addCartItem(id,quantity),onSuccess:c=>{qc.setQueryData(['cart'],c);setToast('Added to your bag')},onError:e=>setToast(e.message)});
const update=useMutation({mutationFn:({id,quantity}:{id:string;quantity:number})=>quantity===0?api.removeCartItem(id):api.updateCartItem(id,quantity),onSuccess:c=>{qc.setQueryData(['cart'],c);setToast('Your bag has been updated')},onError:e=>setToast(e.message)});
const clear=useMutation({mutationFn:api.clearCart,onSuccess:c=>{qc.setQueryData(['cart'],c);setToast('Your bag is now empty')},onError:e=>setToast(e.message)});
const setUser=(u:User|null)=>{qc.setQueryData(['user'],u);qc.removeQueries({queryKey:['orders']});qc.removeQueries({queryKey:['order']});qc.invalidateQueries({queryKey:['cart']})};
return {user:user.data??null,userLoading:user.isPending,userError:user.error,cart,add,update,clear,setUser,notify:setToast,toast};}
export function StoreProvider({children}:{children:ReactNode}){const value=useStore();return <Context.Provider value={value}>{children}<div className={'toast '+(value.toast?'visible':'')} role="status" aria-live="polite">{value.toast}</div></Context.Provider>}
export const useStoreContext=()=>{const c=useContext(Context);if(!c)throw new Error('StoreProvider is required');return c};
