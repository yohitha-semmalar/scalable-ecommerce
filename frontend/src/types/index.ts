export interface Category {slug:string;name:string;description:string}
export interface Product {id:string;name:string;category:string;price:number;originalPrice?:number;description:string;images:string[];stock:number;specifications:Record<string,string>;createdAt:string;featured:boolean}
export interface User {id:string;name:string;email:string}
export interface AuthResponse {user:User}
export interface CartItem {product:Product;quantity:number}
export interface Cart {items:CartItem[];subtotal:number;shipping:number;tax:number;total:number}
export interface Address {name:string;email:string;phone:string;address:string;city:string;state?:string;postalCode:string}
export interface Order extends Cart {id:string;customerId:string;createdAt:string;status:string;shippingAddress:Address}
export type OrderItem=CartItem;
export interface Suggestion {id:string;label:string;productId?:string}
export interface ShortLink {code:string;url:string;productId:string}
export interface CompetitorPrice {source:string;price:number;available:boolean;checkedAt:string;simulated:boolean}
export interface ServiceHealth {status:'healthy'|'degraded'|'unhealthy'|'unavailable';simulated:boolean;services?:{name:string;status:string}[]}
export interface Metric {scenario:string;users:number;throughput:number;average:number;median:number;p95:number;p99:number;errorRate:number}
export interface SystemMetrics {source:string;simulated:boolean;results:Metric[]}
export interface ShardInfo {id:string;physical:string;keys:string[]}
export interface ProductQuery {q?:string;category?:string;min?:number;max?:number;stock?:boolean;sort?:string;page?:number;limit?:number;sale?:boolean}
export interface ProductResult {items:Product[];total:number}
