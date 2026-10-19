import React from 'react';
import ReactDOM from 'react-dom/client';
import {BrowserRouter} from 'react-router-dom';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import App from './app/App';
import {StoreProvider} from './app/providers';
import './styles/global.css';
const client=new QueryClient({defaultOptions:{queries:{staleTime:30000,retry:false,refetchOnWindowFocus:false},mutations:{retry:false}}});
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><BrowserRouter><QueryClientProvider client={client}><StoreProvider><App/></StoreProvider></QueryClientProvider></BrowserRouter></React.StrictMode>);
