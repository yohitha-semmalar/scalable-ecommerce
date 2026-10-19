import {Component,Suspense,lazy,type ReactNode} from 'react';
import {Routes,Route} from 'react-router-dom';
import Layout from '../components/Layout';
import {Loading} from '../components/ui';
import {Protected} from './Protected';
const Home=lazy(()=>import('../features/Home'));
const Catalogue=lazy(()=>import('../features/Catalogue'));
const ProductDetail=lazy(()=>import('../features/ProductDetail'));
const Auth=lazy(()=>import('../features/Auth'));
const SystemLab=lazy(()=>import('../features/SystemLab'));
const Cart=lazy(()=>import('../features/Commerce').then(m=>({default:m.CartPage})));
const Checkout=lazy(()=>import('../features/Commerce').then(m=>({default:m.Checkout})));
const Account=lazy(()=>import('../features/Commerce').then(m=>({default:m.Account})));
const Orders=lazy(()=>import('../features/Commerce').then(m=>({default:m.Orders})));
const OrderDetail=lazy(()=>import('../features/Commerce').then(m=>({default:m.OrderDetail})));
const Utility=lazy(()=>import('../features/Utility').then(m=>({default:m.Help})));
const Short=lazy(()=>import('../features/Utility').then(m=>({default:m.ShortRedirect})));
const NotFound=lazy(()=>import('../features/Utility').then(m=>({default:m.NotFound})));
export class ErrorBoundary extends Component<{children:ReactNode},{failed:boolean}>{state={failed:false};static getDerivedStateFromError(){return {failed:true}}render(){return this.state.failed?<div className="page"><h1>Let’s try that again.</h1><p>The page encountered an unexpected error.</p><button className="btn" onClick={()=>location.reload()}>Reload ScaleShop</button></div>:this.props.children}}
export default function App(){return <ErrorBoundary><Suspense fallback={<div className="page"><Loading/></div>}><Routes><Route element={<Layout/>}><Route index element={<Home/>}/><Route path="products" element={<Catalogue/>}/><Route path="category/:slug" element={<Catalogue/>}/><Route path="search" element={<Catalogue/>}/><Route path="products/:id" element={<ProductDetail/>}/><Route path="login" element={<Auth key="login"/>}/><Route path="register" element={<Auth key="register" registering/>}/><Route path="cart" element={<Cart/>}/><Route path="checkout" element={<Protected><Checkout/></Protected>}/><Route path="account" element={<Protected><Account/></Protected>}/><Route path="orders" element={<Protected><Orders/></Protected>}/><Route path="orders/:id" element={<Protected><OrderDetail/></Protected>}/><Route path="s/:code" element={<Short/>}/><Route path="system-lab" element={<SystemLab/>}/><Route path="help" element={<Utility/>}/><Route path="policies" element={<Utility policy/>}/><Route path="*" element={<NotFound/>}/></Route></Routes></Suspense></ErrorBoundary>}
