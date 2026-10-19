import {config,http,ApiError,tokenSession} from './apiClient';
import {mock} from '../mocks/adapter';
import type {Address,AuthResponse,Cart,CompetitorPrice,Order,Product,ProductQuery,ProductResult,ServiceHealth,ShortLink,Suggestion,SystemMetrics,User} from '../types';
const query=(q:ProductQuery)=>{const s=new URLSearchParams();Object.entries(q).forEach(([k,v])=>{if(v!==undefined&&v!=='')s.set(k,String(v))});return s.toString()};
export const authPaths={
  legacy:{register:'/api/register',login:'/api/login',me:'/api/users/me'},
  services:{register:'/api/auth/register',login:'/api/auth/login',me:'/api/auth/me'}
} as const;
type Credentials={email:string;password:string};
type Registration=Credentials&{name:string};
// These response shapes are proposals: screenshots specify requests, not responses.
interface AuthWireResponse {user?:User;token?:string;accessToken?:string}
export interface ServiceAddress {fullName:string;phone:string;line1:string;city:string;state:string;postalCode:string}
export type WireOrder=Omit<Order,'shippingAddress'>&{shippingAddress:Address|ServiceAddress};
export const toServiceAddress=(a:Address):ServiceAddress=>({fullName:a.name,phone:a.phone,line1:a.address,city:a.city,state:a.state||'',postalCode:a.postalCode});
export function normalizeOrder(order:WireOrder):Order{
  const a=order.shippingAddress;
  return {...order,shippingAddress:'fullName' in a?{name:a.fullName,email:'',phone:a.phone,address:a.line1,city:a.city,state:a.state,postalCode:a.postalCode}:a};
}
function makeAuthApi(profile:keyof typeof authPaths){
  const paths=authPaths[profile];
  const getMe=async():Promise<User|null>=>{
    if(config.authMode==='bearer'&&!tokenSession.get())return null;
    try{return await http<User|null>(paths.me,'GET',undefined,undefined,{}, {base:config.authBase})}
    catch(e){if(e instanceof ApiError&&e.status===401)return null;throw e}
  };
  const finish=async(r:AuthWireResponse):Promise<AuthResponse>=>{
    const token=r?.accessToken||r?.token;
    if(config.authMode==='bearer'){
      if(!token)throw new ApiError('Login response did not include token or accessToken. Confirm the authentication response contract.',502);
      tokenSession.set(token);
    }
    try{
      const user=r?.user||await getMe();
      if(!user?.id||!user.name||!user.email)throw new ApiError('The profile response must include id, name and email. Confirm the backend response contract.',502);
      return {user};
    }catch(e){tokenSession.clear();throw e}
  };
  const loginUser=async(data:Credentials)=>{
    const r=await http<AuthWireResponse>(paths.login,'POST',{email:data.email,password:data.password},undefined,{}, {base:config.authBase,authenticated:false});
    return finish(r);
  };
  const registerUser=async(data:Registration)=>{
    const r=await http<AuthWireResponse>(paths.register,'POST',data,undefined,{}, {base:config.authBase,authenticated:false});
    // Registration may return a created user without a session/token. Sign in once.
    if(config.authMode==='bearer'&&!r?.token&&!r?.accessToken){
      try{return await loginUser(data)}catch(e){throw new ApiError('Account created, but sign-in failed. Open Sign in to continue. '+(e as Error).message,e instanceof ApiError?e.status:502,e instanceof ApiError?e.retryAfter:0)}
    }
    return finish(r);
  };
  return {registerUser,loginUser,getMe};
}
export const legacyAuthApi=makeAuthApi('legacy');
export const serviceAuthApi=makeAuthApi('services');
const cartRequest=<T,>(path:string,method='GET',body?:unknown)=>http<T>(path,method,body,undefined,{}, {base:config.cartBase});
const getCart=()=>cartRequest<Cart>('/api/cart');
const cartResult=async(request:Promise<Cart|undefined>)=>(await request)??await getCart();
// Both cart contracts remain callable, independently of the active configuration.
export const legacyCartApi={
  addCartItem:(productId:string,quantity:number)=>cartResult(cartRequest<Cart>('/api/cart/items','POST',{productId,quantity,operation:'increment'})),
  updateCartItem:(productId:string,quantity:number)=>cartResult(cartRequest<Cart>('/api/cart/items','POST',{productId,quantity,operation:'set'})),
  removeCartItem:(productId:string)=>cartResult(cartRequest<Cart>('/api/cart/items','POST',{productId,quantity:0,operation:'set'}))
};
export const serviceCartApi={
  addCartItem:(productId:string,quantity:number)=>cartResult(cartRequest<Cart>('/api/cart/items','POST',{productId,quantity})),
  updateCartItem:(productId:string,quantity:number)=>cartResult(cartRequest<Cart>('/api/cart/items/'+encodeURIComponent(productId),'PATCH',{quantity})),
  removeCartItem:(productId:string)=>cartResult(cartRequest<Cart>('/api/cart/items/'+encodeURIComponent(productId),'DELETE'))
};
export const httpAdapter={
  registerUser:(data:Registration)=>(config.contract==='legacy'?legacyAuthApi:serviceAuthApi).registerUser(data),
  loginUser:(data:Credentials)=>(config.contract==='legacy'?legacyAuthApi:serviceAuthApi).loginUser(data),
  getMe:()=>(config.contract==='legacy'?legacyAuthApi:serviceAuthApi).getMe(),
  logout:async()=>{if(config.authMode==='cookie')await http<void>('/api/logout','POST',undefined,undefined,{}, {base:config.authBase});tokenSession.clear()},
  // Retained for servers that expose the original logout route, including token revocation.
  logoutOnServer:async()=>{await http<void>('/api/logout','POST',undefined,undefined,{}, {base:config.authBase});tokenSession.clear()},
  getProducts:(q:ProductQuery={},signal?:AbortSignal)=>http<ProductResult>('/api/products?'+query(q),'GET',undefined,signal),
  searchProducts:(q:ProductQuery={},signal?:AbortSignal)=>http<ProductResult>('/api/search?'+query(q),'GET',undefined,signal),
  getProductById:(id:string,signal?:AbortSignal)=>http<Product>('/api/products/'+encodeURIComponent(id),'GET',undefined,signal),
  getSuggestions:(q:string,signal?:AbortSignal)=>http<Suggestion[]>('/api/suggestions?q='+encodeURIComponent(q),'GET',undefined,signal),
  getCart,
  addCartItem:(productId:string,quantity:number)=>(config.contract==='legacy'?legacyCartApi:serviceCartApi).addCartItem(productId,quantity),
  updateCartItem:(productId:string,quantity:number)=>(config.cartWriteMode==='patch'?serviceCartApi:legacyCartApi).updateCartItem(productId,quantity),
  removeCartItem:(productId:string)=>(config.cartWriteMode==='patch'?serviceCartApi:legacyCartApi).removeCartItem(productId),
  clearCart:()=>cartResult(cartRequest<Cart>('/api/cart','DELETE')),
  createOrder:async(shippingAddress:Address,key:string)=>{
    const payload=config.contract==='legacy'?{shippingAddress,paymentMethod:'demo-no-payment'}:{shippingAddress:toServiceAddress(shippingAddress)};
    return normalizeOrder(await http<WireOrder>('/api/orders','POST',payload,undefined,{'Idempotency-Key':key},{base:config.orderBase}));
  },
  getOrders:async()=>(await http<WireOrder[]>('/api/orders','GET',undefined,undefined,{}, {base:config.orderBase})).map(normalizeOrder),
  getOrder:async(id:string)=>normalizeOrder(await http<WireOrder>('/api/orders/'+encodeURIComponent(id),'GET',undefined,undefined,{}, {base:config.orderBase})),
  createShortLink:(id:string)=>http<ShortLink>('/api/share/'+encodeURIComponent(id),'POST'),
  // Real /s/:code is a browser redirect, not a JSON API.
  resolveShortLink:async(code:string):Promise<{productId:string}>=>{location.assign(config.base+'/s/'+encodeURIComponent(code));return new Promise(()=>{})},
  getCompetitorPrices:(id:string)=>http<CompetitorPrice[]>('/api/prices/'+encodeURIComponent(id)),
  getServiceHealth:()=>http<ServiceHealth>('/api/health'),getSystemMetrics:()=>http<SystemMetrics>('/api/metrics')
};
export const mockAdapter={...mock,searchProducts:mock.getProducts,removeCartItem:(id:string)=>mock.updateCartItem(id,0),getOrder:(id:string)=>mock.getOrder(id)};
export const selectAdapter=(mode:string)=>mode==='api'?httpAdapter:mockAdapter;
export const api=selectAdapter(config.mode);
