import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { 
  Search, SlidersHorizontal, RefreshCw, Zap, ExternalLink, 
  CheckCircle2, AlertTriangle, ShieldCheck, Database, Layers, Sparkles 
} from 'lucide-react';
import { Loading, ErrorState, Empty } from '../components/ui';
import { useQuery, useQueryClient } from '@tanstack/react-query';

interface ProductOfferItem {
  productId: string;
  name: string;
  brand: string;
  model?: string;
  variant?: string;
  category?: string;
  price: number;
  originalPrice?: number;
  currency: string;
  rating?: number;
  reviewsCount?: number;
  discountPercent?: number;
  availability: boolean;
  stock?: number;
  productUrl?: string;
  imageUrl?: string;
  source: string;
  mock: boolean;
  retrievalTimestamp: string;
}

interface NormalizedProductItem {
  canonicalId: string;
  title: string;
  brand: string;
  model?: string;
  generation?: string;
  variant?: string;
  category: string;
  primaryImage?: string;
  lowestPrice: number;
  lowestPriceSource: string;
  highestPrice: number;
  priceSpread: number;
  averagePrice: number;
  averageRating?: number;
  totalReviews: number;
  anyAvailable: boolean;
  offerCount: number;
  offers: ProductOfferItem[];
  mock: boolean;
}

interface PriceSearchResponse {
  query: string;
  totalProducts: number;
  totalOffers: number;
  products: NormalizedProductItem[];
  sourceStatus: Record<string, string>;
  cacheStatus: 'HIT' | 'MISS' | 'BYPASS' | 'STALE';
  redisAvailable: boolean;
  mock: boolean;
  cachedAt?: string;
  retrievalTimestamp: string;
  latencyMs: number;
  page: number;
  limit: number;
  totalPages: number;
}

const CRAWLER_API_BASE = 'http://localhost:8081';


export default function PriceComparison() {
  const [sp, setSp] = useSearchParams();
  const query = sp.get('q') || 'AirPods';
  const brand = sp.get('brand') || '';
  const category = sp.get('category') || '';
  const minPrice = sp.get('min') || '';
  const maxPrice = sp.get('max') || '';
  const sort = sp.get('sort') || 'price_asc';
  const availableOnly = sp.get('available') === 'true';

  const [filtersOpen, setFiltersOpen] = useState(false);
  const [inputVal, setInputVal] = useState(query);
  const queryClient = useQueryClient();

  const queryKey = [
    'price-comparison',
    query,
    brand,
    category,
    minPrice,
    maxPrice,
    sort,
    availableOnly
  ];

  const fetchPrices = async (
    refresh = false
  ): Promise<PriceSearchResponse> => {
    const params = new URLSearchParams();

    if (query) params.set('q', query);
    if (brand) params.set('brand', brand);
    if (category) params.set('category', category);
    if (minPrice) params.set('minPrice', minPrice);
    if (maxPrice) params.set('maxPrice', maxPrice);

    if (availableOnly) {
      params.set('availableOnly', 'true');
    }

    if (sort) params.set('sort', sort);

    if (refresh) {
      params.set('refresh', 'true');
    }

    const res = await fetch(
      `${CRAWLER_API_BASE}/api/prices/search?${params.toString()}`
    );

    if (!res.ok) {
      throw new Error(`Crawler returned HTTP ${res.status}`);
    }

    return (await res.json()) as PriceSearchResponse;
  };

  const q = useQuery({
    queryKey,
    queryFn: () => fetchPrices(false),
    staleTime: 0,
    refetchOnWindowFocus: false
  });

  const handleSearchSubmit = (
    e: React.FormEvent<HTMLFormElement>
  ) => {
    e.preventDefault();

    const newSp = new URLSearchParams(sp);

    if (inputVal.trim()) {
      newSp.set('q', inputVal.trim());
    } else {
      newSp.delete('q');
    }

    setSp(newSp);
  };

  const updateParam = (key: string, val: string) => {
    const newSp = new URLSearchParams(sp);

    if (val) {
      newSp.set(key, val);
    } else {
      newSp.delete(key);
    }

    setSp(newSp);
  };

  const handleFreshCrawl = async () => {
    if (q.isFetching) return;

    try {
      const result = await fetchPrices(true);

      // Update the displayed result with the fresh-crawl response.
      queryClient.setQueryData(queryKey, result);
    } catch (error) {
      console.error('Fresh crawl failed:', error);
    }
  };

  const data = q.data;

  return (
    <div className="page" style={{ maxWidth: '1440px', margin: 'auto', padding: '30px 4% 65px' }}>
      <div className="catalogue-heading">
        <div>
          <h1>Multi-Retailer Price Comparison</h1>
        </div>
        
        <button
          className="btn secondary"
          disabled={q.isFetching}
          onClick={handleFreshCrawl}
          style={{
            display: 'inline-flex',
            gap: '8px',
            alignItems: 'center'
          }}
        >
          <RefreshCw
            size={15}
            className={q.isFetching ? 'animate-spin' : ''}
          />
          {q.isFetching ? 'Crawling…' : 'Bypass Cache & Crawl Fresh'}
        </button>

      </div>

      {/* Observability Banner */}
      {data && (
        <div style={{
          background: '#f1f2ed',
          border: '1px solid #ced3c3',
          padding: '16px 20px',
          marginBottom: '28px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '15px',
          fontSize: '12px'
        }}>
          <div style={{ display: 'flex', gap: '20px', alignItems: 'center', flexWrap: 'wrap' }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
              <Database size={15} color="#556048" />
              <strong>Cache:</strong> 
              <span className="best" style={{
                background: data.cacheStatus === 'HIT' ? '#d4edda' : '#fff3cd',
                color: data.cacheStatus === 'HIT' ? '#155724' : '#856404'
              }}>
                {data.cacheStatus} ({data.latencyMs} ms)
              </span>
            </span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
              <Layers size={15} color="#556048" />
              <strong>Sources queried:</strong> {Object.keys(data.sourceStatus || {}).length}
            </span>
          </div>
          <div style={{ color: '#68725e', fontSize: '11px' }}>
            Last checked: {new Date(data.retrievalTimestamp).toLocaleTimeString()}
          </div>
        </div>
      )}

      {/* Search Input Bar */}
      <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '10px', marginBottom: '25px' }}>
        <div className="search-wrap" style={{ margin: 0, maxWidth: 'none', flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', border: '1px solid #d9dad4', background: '#f0efea', padding: '0 14px', height: '46px', gap: '12px' }}>
            <Search size={18} color="#656a6e" />
            <input 
              value={inputVal}
              onChange={(e) => setInputVal(e.target.value)}
              placeholder="Search AirPods, iPhone 15, Levi's Jeans, Air Fryer..."
              style={{ background: 'transparent', border: 0, outline: 'none', width: '100%', fontSize: '13px' }}
            />
          </div>
        </div>
        <button type="submit" className="btn" style={{ minHeight: '46px' }}>
          Compare Prices
        </button>
      </form>

      {/* Suggested Quick Searches */}
      <div style={{ display: 'flex', gap: '10px', marginBottom: '30px', flexWrap: 'wrap', alignItems: 'center' }}>
        <span style={{ fontSize: '11px', color: '#656a6e' }}>Popular comparisons:</span>
        {['AirPods', 'iPhone 15', 'Samsung Galaxy S24', "Levi's Jeans", 'Air Fryer', 'Mixer Grinder'].map((term) => (
          <button 
            key={term}
            type="button"
            className="plain"
            onClick={() => { setInputVal(term); updateParam('q', term); }}
            style={{ fontSize: '11px', background: '#e9e8e0', padding: '4px 10px', borderRadius: '2px', textDecoration: 'none' }}
          >
            {term}
          </button>
        ))}
      </div>

      {/* Main Layout: Filters + Product Cards */}
      <div className="catalogue-layout">
        {/* Filters Sidebar */}
        <aside className={'filters ' + (filtersOpen ? 'show' : '')}>
          <div className="row">
            <h3>Filters</h3>
            <button className="plain" onClick={() => setSp({ q: query })}>Clear</button>
          </div>

          <fieldset>
            <legend>Category</legend>
            {['', 'Electronics', 'Fashion', 'Kitchen', 'Grocery'].map((cat) => (
              <label key={cat}>
                <input 
                  type="radio" 
                  name="category" 
                  checked={category === cat}
                  onChange={() => updateParam('category', cat)}
                />
                {cat || 'All Categories'}
              </label>
            ))}
          </fieldset>

          <fieldset>
            <legend>Price Range (₹)</legend>
            <div className="price-fields">
              <label>
                Min
                <input 
                  type="number" 
                  placeholder="0" 
                  value={minPrice}
                  onChange={(e) => updateParam('min', e.target.value)}
                />
              </label>
              <span>—</span>
              <label>
                Max
                <input 
                  type="number" 
                  placeholder="Any" 
                  value={maxPrice}
                  onChange={(e) => updateParam('max', e.target.value)}
                />
              </label>
            </div>
          </fieldset>

          <fieldset>
            <legend>Availability</legend>
            <label>
              <input 
                type="checkbox"
                checked={availableOnly}
                onChange={(e) => updateParam('available', e.target.checked ? 'true' : '')}
              />
              In stock only
            </label>
          </fieldset>
        </aside>

        {/* Content Area */}
        <div>
          {/* Toolbar */}
          <div className="catalogue-toolbar">
            <button className="btn secondary filter-toggle" onClick={() => setFiltersOpen(!filtersOpen)}>
              <SlidersHorizontal size={14} /> Filters
            </button>
            <span style={{ fontSize: '12px', color: '#656a6e' }}>
              {data ? `${data.totalProducts} matched products (${data.totalOffers} competitor offers)` : 'Searching…'}
            </span>
            <label style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '8px' }}>
              Sort by
              <select value={sort} onChange={(e) => updateParam('sort', e.target.value)}>
                <option value="price_asc">Lowest Price First</option>
                <option value="price_desc">Highest Price First</option>
                <option value="rating">Top Customer Rating</option>
                <option value="discount">Highest Discount</option>
                <option value="spread">Largest Price Spread</option>
              </select>
            </label>
          </div>

          {/* Results State */}
          {q.isPending ? (
            <Loading />
          ) : q.isError ? (
            <ErrorState error={q.error} retry={() => q.refetch()} />
          ) : !data || data.products.length === 0 ? (
            <Empty title="No matching products found">
              <p>Try searching for “AirPods”, “iPhone 15”, “Jeans”, or “Mixer Grinder”.</p>
              <button className="btn" onClick={() => { setInputVal('AirPods'); updateParam('q', 'AirPods'); }}>
                Search AirPods
              </button>
            </Empty>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '30px' }}>
              {data.products.map((prod) => (
                <div 
                  key={prod.canonicalId}
                  style={{
                    background: '#fff',
                    border: '1px solid var(--line)',
                    padding: '24px',
                    borderRadius: '2px',
                    display: 'grid',
                    gridTemplateColumns: '180px 1fr',
                    gap: '24px'
                  }}
                >
                  {/* Left: Product Image & Badges */}
                  <div>
                    <div style={{ width: '100%', aspectRatio: '1/1', background: '#f5f5f0', position: 'relative', overflow: 'hidden' }}>
                      <img
                        src={
                          prod.primaryImage ||
                          'https://images.unsplash.com/photo-1546435770-a3e426bf472b?auto=format&fit=crop&w=600&q=80'
                        }
                        alt={prod.title}
                        onError={(e) => {
                          e.currentTarget.onerror = null;
                          e.currentTarget.src =
                            'https://images.unsplash.com/photo-1546435770-a3e426bf472b?auto=format&fit=crop&w=600&q=80';
                        }}
                        style={{
                          width: '100%',
                          height: '100%',
                          objectFit: 'contain',
                          padding: '12px',
                          boxSizing: 'border-box'
                        }}
                      />
                    </div>
                    <div style={{ marginTop: '12px', fontSize: '11px', color: '#656a6e', textAlign: 'center' }}>
                      {prod.offerCount} stores compared
                    </div>
                  </div>

                  {/* Right: Details & Offer Comparison Table */}
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '15px' }}>
                      <div>
                        <h3 style={{ fontSize: '18px', marginBottom: '8px' }}>{prod.title}</h3>
                        {prod.variant && <span style={{ fontSize: '11px', background: '#f0f0ea', padding: '2px 8px', borderRadius: '2px', marginRight: '8px' }}>{prod.variant}</span>}
                        {prod.averageRating && (
                          <span style={{ fontSize: '11px', color: '#855d14' }}>
                            ★ {prod.averageRating.toFixed(1)} ({prod.totalReviews.toLocaleString()} reviews)
                          </span>
                        )}
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <span style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.8px', color: '#656a6e', display: 'block' }}>Best Price</span>
                        <div style={{ fontSize: '22px', fontWeight: 600, color: '#17191c' }}>
                          ₹{prod.lowestPrice.toLocaleString()}
                        </div>
                        <small style={{ color: '#2d6a4f', fontWeight: 500 }}>on {prod.lowestPriceSource}</small>
                        {prod.priceSpread > 0 && (
                          <div style={{ fontSize: '11px', color: '#9c472f', marginTop: '4px' }}>
                            Save up to ₹{prod.priceSpread.toLocaleString()}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Offers Comparison Table */}
                    <div className="table-scroll" style={{ marginTop: '18px', borderTop: '1px solid var(--line)' }}>
                      <table style={{ width: '100%', fontSize: '12px', borderCollapse: 'collapse' }}>
                        <thead>
                          <tr style={{ background: '#faf9f6' }}>
                            <th style={{ padding: '10px 12px' }}>Retailer</th>
                            <th style={{ padding: '10px 12px' }}>Offered Price</th>
                            <th style={{ padding: '10px 12px' }}>Difference</th>
                            <th style={{ padding: '10px 12px' }}>Availability</th>
                            <th style={{ padding: '10px 12px', textAlign: 'right' }}>Action</th>
                          </tr>
                        </thead>
                        <tbody>
                          {prod.offers.map((offer) => {
                            const diff = offer.price - prod.lowestPrice;
                            const isBest = offer.price === prod.lowestPrice && offer.availability;

                            return (
                              <tr key={offer.productId || offer.source} style={{ borderBottom: '1px solid #eeeee6' }}>
                                <td style={{ padding: '10px 12px', fontWeight: 500 }}>
                                  {offer.source}
                                  {isBest && (
                                    <span className="best" style={{ marginLeft: '8px' }}>
                                      Lowest Price
                                    </span>
                                  )}
                                </td>
                                <td style={{ padding: '10px 12px', fontWeight: 600 }}>
                                  ₹{offer.price.toLocaleString()}
                                  {offer.discountPercent && offer.discountPercent > 0 ? (
                                    <span style={{ fontSize: '10px', color: '#2d6a4f', marginLeft: '6px' }}>
                                      ({offer.discountPercent}% OFF)
                                    </span>
                                  ) : null}
                                </td>
                                <td style={{ padding: '10px 12px', color: diff === 0 ? '#2d6a4f' : '#656a6e' }}>
                                  {diff === 0 ? 'Best offer' : `+₹${diff.toLocaleString()}`}
                                </td>
                                <td style={{ padding: '10px 12px' }}>
                                  {offer.availability ? (
                                    <span style={{ color: '#2d6a4f', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                                      <CheckCircle2 size={13} /> In stock
                                    </span>
                                  ) : (
                                    <span style={{ color: '#9c472f', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                                      <AlertTriangle size={13} /> Out of stock
                                    </span>
                                  )}
                                </td>
                                <td style={{ padding: '10px 12px', textAlign: 'right' }}>
                                  {offer.productUrl ? (
                                    <a 
                                      href={offer.productUrl} 
                                      target="_blank" 
                                      rel="noopener noreferrer" 
                                      className="text-link"
                                      style={{ fontSize: '11px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                                    >
                                      View <ExternalLink size={11} />
                                    </a>
                                  ) : (
                                    <span style={{ color: '#999', fontSize: '11px' }}>Direct link</span>
                                  )}
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
