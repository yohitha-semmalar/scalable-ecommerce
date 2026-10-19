import type {CartItem,Cart} from '../types';
export const money=(value:number)=>new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0}).format(value);
export function totals(items:CartItem[]):Cart{const subtotal=items.reduce((s,i)=>s+i.product.price*i.quantity,0);const shipping=subtotal===0||subtotal>=2500?0:99;return {items,subtotal,shipping,tax:0,total:subtotal+shipping}}
