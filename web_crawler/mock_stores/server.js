
const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

const STORES = {
  amazon: { name: 'Amazon India', port: 8085, dataFile: 'amazon.json' },
  flipkart: { name: 'Flipkart', port: 8086, dataFile: 'flipkart.json' },
  meesho: { name: 'Meesho', port: 8087, dataFile: 'meesho.json' },
  instamart: { name: 'Swiggy Instamart', port: 8088, dataFile: 'instamart.json' }
};

const IMAGE_DIR = path.join(__dirname, 'public', 'images');

function loadStoreData(dataFile) {
  const filePath = path.join(__dirname, 'data', dataFile);

  try {
    const data = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    return Array.isArray(data) ? data : [];
  } catch (err) {
    console.error(`Failed to load ${filePath}: ${err.message}`);
    return [];
  }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function money(value) {
  const amount = Number(value);
  return Number.isFinite(amount)
    ? `₹${amount.toLocaleString('en-IN')}`
    : 'Price unavailable';
}

function getProductId(product) {
  return String(product.productId || product.canonicalId || '');
}

// Image URLs may be local files, absolute HTTP(S) URLs, or relative paths.
function getImageUrl(product, port) {
  const image = String(product.imageUrl || product.image || '').trim();

  if (!image) return '';

  if (/^https?:\/\//i.test(image)) {
    return image;
  }

  // A filename such as "earbuds.jpg" maps to public/images/earbuds.jpg.
  const filename = path.basename(image);

  return `http://localhost:${port}/images/${encodeURIComponent(filename)}`;
}

function getProductUrl(port, product) {
  return `http://localhost:${port}/store/product/${encodeURIComponent(getProductId(product))}`;
}

function pageTemplate(title, storeName, content) {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escapeHtml(title)} | ${escapeHtml(storeName)}</title>
  <style>
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: Arial, Helvetica, sans-serif;
      color: #202124;
      background: #f4f6f8;
    }
    header {
      background: #172337;
      color: white;
      padding: 22px max(5%, calc((100% - 1100px) / 2));
    }
    header h1 { margin: 0 0 8px; font-size: 26px; }
    header p { margin: 0; color: #dbe4f0; }
    main { max-width: 1100px; margin: 28px auto; padding: 0 20px; }
    .search { display: flex; gap: 10px; margin-bottom: 24px; }
    .search input {
      flex: 1; min-width: 0; padding: 12px;
      border: 1px solid #cbd2da; border-radius: 8px; font-size: 16px;
    }
    button, .view-link {
      display: inline-block; border: 0; border-radius: 7px;
      background: #1769aa; color: white; padding: 11px 16px;
      font-size: 14px; text-decoration: none; cursor: pointer;
    }
    .product-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
      gap: 18px;
    }
    .product-card, .product-detail {
      background: white; border: 1px solid #e1e5ea;
      border-radius: 12px; padding: 18px;
    }
    .product-card h2 { font-size: 18px; line-height: 1.4; }
    .product-image-wrap {
      height: 210px; display: flex; align-items: center;
      justify-content: center; background: #f8f9fb;
      border-radius: 8px; overflow: hidden;
    }
    .product-image {
      display: block; width: 100%; height: 100%;
      object-fit: contain; padding: 8px;
    }
    .product-detail-layout {
      display: grid; grid-template-columns: minmax(220px, 0.9fr) 1.1fr;
      gap: 28px; align-items: start;
    }
    .product-detail-layout .product-image-wrap { height: 360px; }
    .brand { color: #626b76; }
    .price { font-size: 24px; font-weight: bold; margin: 16px 0; }
    .availability { color: #267344; font-weight: 600; }
    .unavailable { color: #b42318; font-weight: 600; }
    .meta { color: #68717d; font-size: 13px; margin: 8px 0; }
    .back-link { display: inline-block; margin-bottom: 20px; }
    .empty {
      padding: 30px; text-align: center; background: white;
      border-radius: 10px;
    }
    footer {
      max-width: 1100px; margin: 30px auto; padding: 0 20px;
      color: #727b86; font-size: 12px;
    }
    @media (max-width: 600px) {
      .search { flex-direction: column; }
      .product-detail-layout { grid-template-columns: 1fr; }
      .product-detail-layout .product-image-wrap { height: 280px; }
    }
  </style>
</head>
<body>
  <header>
    <h1>${escapeHtml(storeName)}</h1>
    <p>Product catalogue and availability</p>
  </header>
  <main>${content}</main>
  <footer>ScaleShop simulated retailer storefront</footer>
</body>
</html>`;
}

function renderProductCard(product, storeConfig, port) {
  const id = getProductId(product);
  const productUrl = getProductUrl(port, product);
  const available = Boolean(product.availability);
  const imageUrl = getImageUrl(product, port);

  return `
    <article class="product-card"
      data-product-id="${escapeHtml(id)}"
      data-canonical-id="${escapeHtml(product.canonicalId || '')}">
      <div class="product-image-wrap">
        ${imageUrl ? `
          <img class="product-image"
            src="${escapeHtml(imageUrl)}"
            alt="${escapeHtml(product.name || 'Product')}"
            loading="lazy"
            onerror="this.style.display='none'">
        ` : '<span class="meta">Image unavailable</span>'}
      </div>
      <p class="brand product-brand">${escapeHtml(product.brand || '')}</p>
      <h2 class="product-name">${escapeHtml(product.name || 'Unnamed product')}</h2>
      <p class="meta product-id">Product ID: ${escapeHtml(id)}</p>
      <p class="price product-price" data-price="${escapeHtml(product.price)}">
        ${money(product.price)}
      </p>
      <p class="product-rating">Rating: ${escapeHtml(product.rating ?? 'N/A')}</p>
      <p class="product-availability ${available ? 'availability' : 'unavailable'}">
        ${available ? 'In stock' : 'Currently unavailable'}
      </p>
      <a class="view-link product-url" href="${escapeHtml(productUrl)}">
        View Product
      </a>
    </article>`;
}

function renderSearchPage(storeConfig, products, query, port) {
  const cards = products.map(product =>
    renderProductCard(product, storeConfig, port)
  ).join('');

  const content = `
    <form class="search" action="/mock-store/search" method="get">
      <input name="q" value="${escapeHtml(query)}"
        placeholder="Search products, brands or categories">
      <button type="submit">Search</button>
    </form>
    <p class="result-count">${products.length} products found</p>
    ${cards
      ? `<section class="product-grid">${cards}</section>`
      : '<div class="empty">No matching products found. Try another search.</div>'}
  `;

  return pageTemplate(
    query ? `Search results for ${query}` : 'All products',
    storeConfig.name,
    content
  );
}

function renderProductPage(storeConfig, product, port) {
  const id = getProductId(product);
  const available = Boolean(product.availability);
  const productUrl = getProductUrl(port, product);
  const imageUrl = getImageUrl(product, port);

  const content = `
    <a class="back-link"
      href="/mock-store/search?q=${encodeURIComponent(product.name || '')}">
      ← Back to search results
    </a>
    <article class="product-detail"
      data-product-id="${escapeHtml(id)}"
      data-canonical-id="${escapeHtml(product.canonicalId || '')}">
      <div class="product-detail-layout">
        <div class="product-image-wrap">
          ${imageUrl ? `
            <img class="product-image"
              src="${escapeHtml(imageUrl)}"
              alt="${escapeHtml(product.name || 'Product')}"
              onerror="this.style.display='none'">
          ` : '<span class="meta">Image unavailable</span>'}
        </div>
        <div>
          <p class="brand product-brand">${escapeHtml(product.brand || '')}</p>
          <h2 class="product-name">${escapeHtml(product.name || 'Unnamed product')}</h2>
          <p class="meta product-id">Product ID: ${escapeHtml(id)}</p>
          <p class="meta product-category">
            Category: ${escapeHtml(product.category || 'General')}
          </p>
          <p class="price product-price" data-price="${escapeHtml(product.price)}">
            ${money(product.price)}
          </p>
          <p class="product-rating">Rating: ${escapeHtml(product.rating ?? 'N/A')}</p>
          <p class="${available ? 'availability' : 'unavailable'} product-availability">
            ${available ? 'In stock' : 'Currently unavailable'}
          </p>
          <p>${escapeHtml(product.description || 'Product details and pricing.')}</p>
          <a class="view-link product-url" href="${escapeHtml(productUrl)}">
            Product permalink
          </a>
        </div>
      </div>
    </article>
  `;

  return pageTemplate(product.name || 'Product', storeConfig.name, content);
}

function createStoreApp(storeKey, storeConfig, port) {
  const products = loadStoreData(storeConfig.dataFile);

  const server = http.createServer((req, res) => {
    const parsedUrl = url.parse(req.url, true);
    const pathname = parsedUrl.pathname;
    const query = parsedUrl.query;

    res.setHeader('X-Content-Type-Options', 'nosniff');

    if (req.method === 'OPTIONS') {
      res.writeHead(204);
      res.end();
      return;
    }

    if (req.method !== 'GET') {
      res.writeHead(405, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('Method not allowed');
      return;
    }

    // Serve local images from mock_stores/public/images.
    if (pathname.startsWith('/images/')) {
      let filename;

      try {
        filename = decodeURIComponent(pathname.slice('/images/'.length));
      } catch {
        res.writeHead(400);
        res.end('Invalid image path');
        return;
      }

      // Only permit a filename, never a directory traversal path.
      if (!filename || filename !== path.basename(filename)) {
        res.writeHead(400);
        res.end('Invalid image path');
        return;
      }

      const imagePath = path.join(IMAGE_DIR, filename);
      const mimeTypes = {
        '.jpg': 'image/jpeg',
        '.jpeg': 'image/jpeg',
        '.png': 'image/png',
        '.webp': 'image/webp',
        '.gif': 'image/gif',
        '.avif': 'image/avif'
      };

      fs.readFile(imagePath, (err, buffer) => {
        if (err) {
          res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
          res.end('Image not found');
          return;
        }

        res.writeHead(200, {
          'Content-Type': mimeTypes[path.extname(filename).toLowerCase()]
            || 'application/octet-stream',
          'Cache-Control': 'public, max-age=3600'
        });
        res.end(buffer);
      });

      return;
    }

    const delayMs = Math.max(
      0,
      parseInt(query.delayMs || process.env.MOCK_DELAY_MS || '0', 10) || 0
    );
    const failRate = Math.min(1, Math.max(
      0,
      parseFloat(query.failRate || process.env.MOCK_FAIL_RATE || '0') || 0
    ));

    const respond = () => {
      if (pathname === '/health' || pathname === '/mock-store/health') {
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          status: 'UP',
          store: storeConfig.name,
          catalogSize: products.length,
          simulated: true,
          timestamp: new Date().toISOString()
        }));
        return;
      }

      if (pathname === '/' || pathname === '/mock-store/search') {
        const q = String(query.q || '').trim().toLowerCase();
        const category = String(query.category || '').trim().toLowerCase();
        const brand = String(query.brand || '').trim().toLowerCase();
        const minPrice = query.minPrice !== undefined ? Number(query.minPrice) : null;
        const maxPrice = query.maxPrice !== undefined ? Number(query.maxPrice) : null;
        const availableOnly = query.available === 'true'
          || query.availableOnly === 'true';

        const results = products.filter(item => {
          if (q) {
            const searchable = [
              item.name, item.brand, item.model,
              item.canonicalId, item.category, item.productId
            ].filter(Boolean).join(' ').toLowerCase();

            if (!searchable.includes(q)) return false;
          }

          if (category &&
              !String(item.category || '').toLowerCase().includes(category)) {
            return false;
          }

          if (brand &&
              !String(item.brand || '').toLowerCase().includes(brand)) {
            return false;
          }

          if (query.minPrice !== undefined &&
              Number.isFinite(minPrice) && Number(item.price) < minPrice) {
            return false;
          }

          if (query.maxPrice !== undefined &&
              Number.isFinite(maxPrice) && Number(item.price) > maxPrice) {
            return false;
          }

          if (availableOnly && !item.availability) return false;

          return true;
        });

        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(renderSearchPage(storeConfig, results, query.q || '', port));
        return;
      }

      if (pathname.startsWith('/store/product/') ||
          pathname.startsWith('/mock-store/product/')) {
        const prefix = pathname.startsWith('/store/product/')
          ? '/store/product/'
          : '/mock-store/product/';

        let id;

        try {
          id = decodeURIComponent(pathname.slice(prefix.length));
        } catch {
          res.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
          res.end('Invalid product ID');
          return;
        }

        const product = products.find(p =>
          getProductId(p) === id ||
          String(p.productId || '') === id ||
          String(p.canonicalId || '') === id
        );

        if (!product) {
          res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
          res.end(pageTemplate(
            'Product not found',
            storeConfig.name,
            '<div class="empty"><h2>Product not found</h2><p>Try searching the catalogue.</p><a href="/mock-store/search">Back to search</a></div>'
          ));
          return;
        }

        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(renderProductPage(storeConfig, product, port));
        return;
      }

      res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(pageTemplate(
        'Page not found',
        storeConfig.name,
        '<div class="empty"><h2>Page not found</h2><a href="/">Browse products</a></div>'
      ));
    };

    if (failRate > 0 && Math.random() < failRate) {
      setTimeout(() => {
        if (!res.headersSent) {
          res.writeHead(503, { 'Content-Type': 'text/html; charset=utf-8' });
          res.end(pageTemplate(
            'Temporarily unavailable',
            storeConfig.name,
            '<div class="empty"><h2>Store temporarily unavailable</h2><p>Please retry your search.</p></div>'
          ));
        }
      }, delayMs);
      return;
    }

    setTimeout(respond, delayMs);
  });

  return server;
}

const specificStore = process.env.STORE;
const requestedPort = Number.parseInt(process.env.PORT || '', 10);

if (specificStore && STORES[specificStore]) {
  const config = STORES[specificStore];
  const port = Number.isFinite(requestedPort) && requestedPort > 0
    ? requestedPort
    : config.port;

  createStoreApp(specificStore, config, port).listen(port, () => {
    console.log(`[Store] ${config.name}: http://localhost:${port}`);
    console.log(`[Store] HTML search: http://localhost:${port}/mock-store/search?q=phone`);
  });
} else {
  console.log('Starting all four simulated storefronts...');

  for (const [key, config] of Object.entries(STORES)) {
    createStoreApp(key, config, config.port).listen(config.port, () => {
      console.log(`[Store] ${config.name}: http://localhost:${config.port}`);
    });
  }
}
