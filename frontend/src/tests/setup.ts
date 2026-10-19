import '@testing-library/jest-dom/vitest';
import {afterEach,vi} from 'vitest';
import {cleanup} from '@testing-library/react';
import {webcrypto} from 'node:crypto';
Object.defineProperty(globalThis,'crypto',{value:webcrypto,configurable:true});
Object.defineProperty(window,'scrollTo',{value:vi.fn(),writable:true});
class ResizeObserverMock {observe(){}unobserve(){}disconnect(){}}
vi.stubGlobal('ResizeObserver',ResizeObserverMock);
afterEach(()=>{cleanup();vi.restoreAllMocks()});
