import {products,benchmarks} from './data';
import {totals} from '../lib/commerce';
import {ApiError} from '../services/apiClient';
import type {User,Order,Cart,ProductQuery,Address,ShortLink} from '../types';
interface StoredUser extends User {salt:string;digest:string}
interface Database {users:StoredUser[];session:string|null;carts:Record<string,Record<string,number>>;orders:Order[];links:Record<string,string>;sequence:number;stock:Record<string,number>}
const KEY='scaleshop-demo-v1';
const fresh=():Database=>({users:[],session:null,carts:{guest:{}},orders:[],links:{},sequence:1000,stock:{}});
let memory:Database|undefined;
function read():Database {try{return JSON.parse(localStorage.getItem(KEY)||'null')||fresh()}catch{return memory||fresh()}}
function save(d:Database){memory=d;try{localStorage.setItem(KEY,JSON.stringify(d))}catch{throw new ApiError('Browser storage is full or disabled. Enable storage for demo persistence.',507)}}
export const mockControls={latency:180,failNext:false};
export async function delay(signal?:AbortSignal){if(signal?.aborted)throw new DOMException('Aborted','AbortError');await new Promise<void>((resolve,reject)=>{const onAbort=()=>{clearTimeout(t);reject(new DOMException('Aborted','AbortError'))};const t=setTimeout(()=>{signal?.removeEventListener('abort',onAbort);resolve()},mockControls.latency);signal?.addEventListener('abort',onAbort,{once:true})});if(mockControls.failNext){mockControls.failNext=false;throw new ApiError('Simulated service unavailable. Retry this request.',503)}}
function publicUser(u:StoredUser):User{return {id:u.id,name:u.name,email:u.email}}
function current(d:Database){return d.users.find(u=>u.id===d.session)}
function requireUser(d:Database){const u=current(d);if(!u)throw new ApiError('Please sign in to continue.',401);return u}
async function digest(s:string){const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s));return Array.from(new Uint8Array(bytes)).map(x=>x.toString(16).padStart(2,'0')).join('')}
const buckets=new Map<string,{count:number;end:number}>();
export function rateRequest(scope:string,limit=5,windowMs=10000){const now=Date.now();let b=buckets.get(scope);if(!b||now>=b.end)b={count:0,end:now+windowMs};b.count++;buckets.set(scope,b);if(b.count>limit)throw new ApiError('Too many requests. Please wait and try again.',429,Math.ceil((b.end-now)/1000));return {allowed:true,remaining:limit-b.count}}
function allProducts(d=read()){return products.map(p=>({...p,stock:d.stock[p.id]??p.stock}))}
function cart(d:Database):Cart{const records=d.carts[d.session||'guest']||{};return totals(Object.entries(records).flatMap(([id,quantity])=>{const p=allProducts(d).find(x=>x.id===id);return p?[{product:p,quantity}]:[]}))}
function mergeGuest(d:Database,u:User){const own=d.carts[u.id]||{};for(const [id,q] of Object.entries(d.carts.guest||{})){own[id]=Math.min((own[id]||0)+q,allProducts(d).find(p=>p.id===id)?.stock||0);if(!own[id])delete own[id]}d.carts[u.id]=own;d.carts.guest={};d.session=u.id}
export const mock={
async registerUser(data:{name:string;email:string;password:string}){await delay();rateRequest('register');const d=read();const email=data.email.toLowerCase().trim();if(d.users.some(u=>u.email===email))throw new ApiError('An account with this email already exists.',409);const salt=crypto.randomUUID();const passwordDigest=await digest(salt+data.password);const latest=read();if(latest.users.some(u=>u.email===email))throw new ApiError('An account with this email already exists.',409);const u={id:`DEMO-CUS-${++latest.sequence}`,name:data.name.trim(),email,salt,digest:passwordDigest};latest.users.push(u);mergeGuest(latest,u);save(latest);return {user:publicUser(u)}},
async loginUser(data:{email:string;password:string}){await delay();rateRequest('login');const d=read();const u=d.users.find(u=>u.email===data.email.toLowerCase().trim());if(!u||u.digest!==await digest(u.salt+data.password))throw new ApiError('Email or password is incorrect.',400);mergeGuest(d,u);save(d);return {user:publicUser(u)}},
async getMe(){await delay();const u=current(read());return u?publicUser(u):null},
async logout(){await delay();const d=read();d.session=null;save(d)},
async getProducts(q:ProductQuery={},signal?:AbortSignal){await delay(signal);let list=allProducts().filter(p=>(!q.q||p.name.toLowerCase().includes(q.q.toLowerCase()))&&(!q.category||p.category===q.category)&&p.price>=(q.min||0)&&p.price<=(q.max??Infinity)&&(!q.stock||p.stock>0)&&(!q.sale||!!p.originalPrice));if(q.sort==='price-asc')list.sort((a,b)=>a.price-b.price);if(q.sort==='price-desc')list.sort((a,b)=>b.price-a.price);if(q.sort==='newest')list.sort((a,b)=>b.createdAt.localeCompare(a.createdAt));const total=list.length;return {items:list.slice(((q.page||1)-1)*(q.limit||12),(q.page||1)*(q.limit||12)),total}},
async getProductById(id:string,signal?:AbortSignal){await delay(signal);const p=allProducts().find(x=>x.id===id);if(!p)throw new ApiError('This product could not be found.',404);return p},
async getSuggestions(q:string,signal?:AbortSignal){await delay(signal);return allProducts().filter(p=>p.name.toLowerCase().includes(q.toLowerCase())).slice(0,5).map(p=>({id:p.id,label:p.name,productId:p.id}))},
async getCart(){await delay();return cart(read())},
async clearCart(){await delay();const d=read();d.carts[d.session||'guest']={};save(d);return cart(d)},
async addCartItem(id:string,quantity:number){await delay();const d=read();const p=allProducts(d).find(x=>x.id===id);if(!p)throw new ApiError('Product not found.',404);const records=d.carts[d.session||'guest']||{};const next=(records[id]||0)+quantity;if(!Number.isInteger(quantity)||quantity<1||next>p.stock)throw new ApiError(`Only ${p.stock} available. Check your cart quantity.`,409);records[id]=next;d.carts[d.session||'guest']=records;save(d);return cart(d)},
async updateCartItem(id:string,quantity:number){await delay();const d=read();const p=allProducts(d).find(x=>x.id===id);if(!p)throw new ApiError('Product not found.',404);if(!Number.isInteger(quantity)||quantity<0||quantity>p.stock)throw new ApiError(`Only ${p.stock} available.`,409);const records=d.carts[d.session||'guest']||{};if(quantity===0)delete records[id];else records[id]=quantity;d.carts[d.session||'guest']=records;save(d);return cart(d)},
async createOrder(address:Address,key:string){await delay();const d=read();const u=requireUser(d);const existing=d.orders.find(o=>o.id===d.links['order:'+u.id+':'+key]);if(existing)return existing;const c=cart(d);if(!c.items.length)throw new ApiError('Your cart is empty.',400);for(const i of c.items)if(i.quantity>i.product.stock)throw new ApiError(`${i.product.name} has insufficient stock. Update your cart.`,409);const order:Order={...c,id:`DEMO-ORD-${++d.sequence}`,customerId:u.id,createdAt:new Date().toISOString(),status:'Confirmed · demo',shippingAddress:address};for(const i of c.items)d.stock[i.product.id]=i.product.stock-i.quantity;d.orders.unshift(order);d.links['order:'+u.id+':'+key]=order.id;d.carts[u.id]={};save(d);return order},
async getOrders(){await delay();const d=read();const u=requireUser(d);return d.orders.filter(o=>o.customerId===u.id)},
async getOrder(id:string){const orders=await this.getOrders();const order=orders.find(o=>o.id===id);if(!order)throw new ApiError('Order not found for this account.',404);return order},
async createShortLink(id:string):Promise<ShortLink>{await delay();const d=read();if(!products.some(p=>p.id===id))throw new ApiError('Product not found.',404);const code='d'+(++d.sequence).toString(36);d.links[code]=id;save(d);return {code,url:`${location.origin}/s/${code}`,productId:id}},
async resolveShortLink(code:string){await delay();const id=read().links[code];if(!id||!products.some(p=>p.id===id))throw new ApiError('This short link is invalid or unavailable in this browser.',404);return {productId:id}},
async getCompetitorPrices(id:string){await delay();const p=products.find(p=>p.id===id);if(!p)throw new ApiError('Prices unavailable.',404);return [{source:'Market A · demo competitor',price:Math.round(p.price*1.12),available:true,checkedAt:new Date().toISOString(),simulated:true},{source:'Market B · demo competitor',price:Math.round(p.price*.96),available:true,checkedAt:new Date().toISOString(),simulated:true}]},
async getServiceHealth(){await delay();return {status:'healthy' as const,simulated:true,services:['Authentication','Products','Cart & orders','Links & autocomplete','Crawler & pricing'].map(name=>({name,status:'Simulated available'}))}},
async getSystemMetrics(){await delay();return benchmarks},
reset(){save(fresh());buckets.clear();mockControls.failNext=false}
};
