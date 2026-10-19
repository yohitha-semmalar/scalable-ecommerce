const origin=(value:string|undefined)=>(value||'').replace(/\/$/,'');
const contract=import.meta.env.VITE_API_CONTRACT==='legacy'?'legacy':'services';
export const config={
  mode:import.meta.env.VITE_DATA_MODE==='api'?'api':'mock',
  base:origin(import.meta.env.VITE_API_BASE_URL),
  authBase:origin(import.meta.env.VITE_AUTH_BASE_URL),
  cartBase:origin(import.meta.env.VITE_CART_BASE_URL),
  orderBase:origin(import.meta.env.VITE_ORDER_BASE_URL),
  contract,
  authMode:import.meta.env.VITE_AUTH_MODE||(contract==='legacy'?'cookie':'bearer'),
  timeout:Number(import.meta.env.VITE_API_TIMEOUT_MS)||10000,
  cartWriteMode:import.meta.env.VITE_CART_WRITE_MODE||(contract==='legacy'?'post':'patch')
};
// Bearer tokens remain in memory only; never persisted in browser storage.
let accessToken:string|null=null;
export const tokenSession={get:()=>accessToken,set:(token:string|null)=>{accessToken=token},clear:()=>{accessToken=null}};
export class ApiError extends Error {constructor(message:string,public status=500,public retryAfter=0){super(message);this.name='ApiError'}}
export const telemetry:{path:string;status:number;latency:number;at:string}[]=[];
interface RequestOptions {base?:string;authenticated?:boolean}
export async function http<T>(path:string,method='GET',body?:unknown,signal?:AbortSignal,headers:Record<string,string>={},options:RequestOptions={}):Promise<T>{
  const controller=new AbortController();
  const abort=()=>controller.abort();
  signal?.addEventListener('abort',abort);
  if(signal?.aborted)controller.abort();
  const timer=setTimeout(abort,config.timeout);
  const start=performance.now();let status=0;
  const bearer=config.authMode==='bearer'&&options.authenticated!==false?tokenSession.get():null;
  try{
    const r=await fetch((options.base||config.base)+path,{
      method,credentials:config.authMode==='cookie'?'include':'omit',signal:controller.signal,
      headers:{'Content-Type':'application/json',...(bearer?{Authorization:`Bearer ${bearer}`} : {}),...headers},
      body:body===undefined?undefined:JSON.stringify(body)
    });
    status=r.status;
    if(!r.ok){
      const b=await r.json().catch(()=>({}));const retry=r.headers.get('Retry-After');
      throw new ApiError(b.error?.message||b.message||`Request failed (${r.status}).`,r.status,retry?(Number(retry)||Math.max(0,Math.ceil((Date.parse(retry)-Date.now())/1000))):0);
    }
    if(r.status===204)return undefined as T;
    const text=await r.text();
    if(!text.trim())return undefined as T;
    try{return JSON.parse(text) as T}catch{throw new ApiError('The service returned an invalid JSON response.',502)}
  }catch(e){
    if(e instanceof ApiError){
      if(e.status===401&&options.authenticated!==false&&(config.authMode==='cookie'||(bearer&&tokenSession.get()===bearer))){tokenSession.clear();window.dispatchEvent(new Event('session-expired'))}
      throw e;
    }
    if(signal?.aborted)throw e;
    throw new ApiError(controller.signal.aborted?'Request timed out. Please retry.':'Cannot reach the service. Check your connection and API configuration.',503);
  }finally{
    clearTimeout(timer);signal?.removeEventListener('abort',abort);
    telemetry.unshift({path,status,latency:Math.round(performance.now()-start),at:new Date().toISOString()});telemetry.splice(30);
  }
}
