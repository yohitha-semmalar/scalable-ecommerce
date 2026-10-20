package com.teamshop.product.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.teamshop.product.model.Product;
import com.teamshop.product.sharding.ConsistentHashRouter;
import com.teamshop.product.sharding.ShardDefinition;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class ProductService {
    private final ConsistentHashRouter router;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, ShardDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, MongoClient> clients = new ConcurrentHashMap<>();
    private final Map<String, MongoTemplate> templates = new ConcurrentHashMap<>();

    @Value("${MONGO_SHARD1_NAME:shard1}") private String name1;
    @Value("${MONGO_SHARD1_URI:}") private String uri1;
    @Value("${MONGO_SHARD1_DATABASE:products}") private String db1;
    @Value("${MONGO_SHARD2_NAME:shard2}") private String name2;
    @Value("${MONGO_SHARD2_URI:}") private String uri2;
    @Value("${MONGO_SHARD2_DATABASE:products}") private String db2;
    @Value("${MONGO_SHARD3_NAME:shard3}") private String name3;
    @Value("${MONGO_SHARD3_URI:}") private String uri3;
    @Value("${MONGO_SHARD3_DATABASE:products}") private String db3;

    public ProductService(ConsistentHashRouter router, StringRedisTemplate redis) {
        this.router = router;
        this.redis = redis;
    }

    @PostConstruct
    public synchronized void initialize() {
        registerInitial(name1, uri1, db1);
        registerInitial(name2, uri2, db2);
        registerInitial(name3, uri3, db3);
        if (definitions.isEmpty()) throw new IllegalStateException("Configure at least one Atlas shard with MONGO_SHARD1_URI or add an initial shard in application configuration.");
        seedIfEmpty();
    }

    private void registerInitial(String name, String uri, String database) {
        if (uri != null && !uri.isBlank()) register(new ShardDefinition(name, uri, database, router.getVirtualNodes(), true));
    }

    private void register(ShardDefinition shard) {
        validateShard(shard.getName(), shard.getUri(), shard.getDatabase());
        if (definitions.containsKey(shard.getName())) throw new IllegalArgumentException("Shard already exists: " + shard.getName());
        MongoClient client = MongoClients.create(shard.getUri());
        try {
            MongoTemplate template = new MongoTemplate(client, shard.getDatabase());
            template.getDb().runCommand(new Document("ping", 1));
            template.indexOps(Product.class).ensureIndex(new Index().on("category", Sort.Direction.ASC));
            clients.put(shard.getName(), client);
            templates.put(shard.getName(), template);
            definitions.put(shard.getName(), shard);
            router.addShard(shard.getName());
        } catch (RuntimeException ex) {
            client.close();
            throw new IllegalArgumentException("Could not connect to shard " + shard.getName() + "; check Atlas URI, DB user and IP allowlist.", ex);
        }
    }

    private void validateShard(String name, String uri, String database) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,40}")) throw new IllegalArgumentException("Shard name must use letters, digits, _ or - (max 40 chars)");
        if (uri == null || !(uri.startsWith("mongodb://") || uri.startsWith("mongodb+srv://"))) throw new IllegalArgumentException("URI must start with mongodb:// or mongodb+srv://");
        if (database == null || !database.matches("[A-Za-z0-9_-]{1,63}")) throw new IllegalArgumentException("Invalid database name");
    }

    private void seedIfEmpty() {
        long count = 0;
        for (String n : router.getShards()) count += templates.get(n).count(new Query(), Product.class);
        if (count > 0) return;
        List<Product> seed = List.of(
            new Product("P1001","Laptop Pro 14","14-inch laptop with 16GB RAM and SSD","Electronics","NovaTech",74999,12),
            new Product("P1002","Laptop Air 13","Lightweight laptop for students","Electronics","NovaTech",58999,20),
            new Product("P1003","Wireless Mouse","Ergonomic wireless optical mouse","Accessories","ClickPro",899,80),
            new Product("P1004","Mechanical Keyboard","RGB mechanical keyboard","Accessories","KeyWorks",2499,35),
            new Product("P1005","USB-C Hub","Seven-port USB-C adapter","Accessories","ConnectIt",1799,40),
            new Product("P1006","Smartphone X","5G smartphone with OLED display","Mobiles","Orbit",32999,18),
            new Product("P1007","Noise-Cancelling Headphones","Over-ear Bluetooth headphones","Audio","SoundPeak",6999,25),
            new Product("P1008","1080p Webcam","Full HD webcam for meetings","Accessories","ViewMax",2199,30),
            new Product("P1009","Portable SSD 1TB","External solid-state drive","Storage","DataCore",6499,16),
            new Product("P1010","Tablet 10","10-inch tablet for reading and streaming","Electronics","Orbit",18999,14),
            new Product("P1011","Laptop Stand","Adjustable aluminium laptop stand","Accessories","DeskMate",1299,50),
            new Product("P1012","Bluetooth Speaker","Portable speaker with stereo sound","Audio","SoundPeak",2999,22));
        for (Product p : seed) templates.get(router.getShard(p.getId())).save(p);
    }

    public List<Product> all() {
        List<Product> cached = getCache("product:all");
        if (cached != null) return cached;
        List<Product> out = allUncached(); out.sort(Comparator.comparing(Product::getId)); putCache("product:all", out); return out;
    }

    public List<Product> search(String q) { return search(q, null); }

    public List<Product> search(String q, String category) {
        String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String cat = category == null ? "" : category.trim();
        String cacheKey = "product:search:" + term + ":" + cat.toLowerCase(Locale.ROOT);
        List<Product> cached = getCache(cacheKey);
        if (cached != null) return cached;
        List<Product> out = new ArrayList<>();
        for (String n : router.getShards()) {
            List<Criteria> filters = new ArrayList<>();
            if (!term.isEmpty()) filters.add(new Criteria().orOperator(
                Criteria.where("name").regex(Pattern.quote(term), "i"),
                Criteria.where("description").regex(Pattern.quote(term), "i"),
                Criteria.where("category").regex(Pattern.quote(term), "i"),
                Criteria.where("brand").regex(Pattern.quote(term), "i")));
            if (!cat.isEmpty()) filters.add(Criteria.where("category").regex("^" + Pattern.quote(cat) + "$", "i"));
            Query query = new Query();
            if (!filters.isEmpty()) query.addCriteria(new Criteria().andOperator(filters));
            out.addAll(templates.get(n).find(query, Product.class));
        }
        out.sort(Comparator.comparing(Product::getId)); putCache(cacheKey, out); return out;
    }

    public Map<String, Object> addProduct(Product product) {
        if (product.getId() == null || product.getId().isBlank()) throw new IllegalArgumentException("Product id is required");
        String owner = router.getShard(product.getId()); templates.get(owner).save(product); invalidate();
        return Map.of("product", product, "storedOnShard", owner);
    }

    public Product byId(String id) { return templates.get(router.getShard(id)).findById(id, Product.class); }

    public synchronized Map<String, Object> addShard(String name, String uri, String database) {
        if (definitions.containsKey(name)) throw new IllegalArgumentException("Shard already exists");
        List<Product> before = allUncached(); Map<String, String> oldOwner = new HashMap<>();
        for (Product p : before) oldOwner.put(p.getId(), router.getShard(p.getId()));
        register(new ShardDefinition(name, uri, database, router.getVirtualNodes(), true));
        int moved = 0;
        try {
            for (Product p : before) {
                String old = oldOwner.get(p.getId()), dest = router.getShard(p.getId());
                if (!old.equals(dest)) { templates.get(dest).save(p); templates.get(old).remove(Query.query(Criteria.where("_id").is(p.getId())), Product.class); moved++; }
            }
        } catch (RuntimeException ex) { throw new IllegalStateException("Shard registered but migration failed; inspect shard distribution before retrying.", ex); }
        invalidate(); return result("ADD", name, before.size(), moved);
    }

    public synchronized Map<String, Object> removeShard(String name) {
        if (!definitions.containsKey(name)) throw new IllegalArgumentException("Unknown shard: " + name);
        if (router.getShards().size() <= 1) throw new IllegalArgumentException("Cannot remove the last shard");
        List<Product> before = allUncached(); Map<String, String> oldOwner = new HashMap<>();
        for (Product p : before) oldOwner.put(p.getId(), router.getShard(p.getId()));
        router.removeShard(name); int moved = 0;
        try {
            for (Product p : before) {
                String old = oldOwner.get(p.getId());
                if (old.equals(name)) { String dest = router.getShard(p.getId()); templates.get(dest).save(p); templates.get(name).remove(Query.query(Criteria.where("_id").is(p.getId())), Product.class); moved++; }
            }
        } catch (RuntimeException ex) { router.addShard(name); throw new IllegalStateException("Migration failed; shard was restored to the ring. Verify data before retrying.", ex); }
        definitions.remove(name); templates.remove(name); MongoClient client = clients.remove(name); if (client != null) client.close();
        invalidate(); return result("REMOVE", name, before.size(), moved);
    }

    private List<Product> allUncached() { List<Product> out = new ArrayList<>(); for (String n : router.getShards()) out.addAll(templates.get(n).findAll(Product.class)); return out; }
    private Map<String, Object> result(String action, String name, int total, int moved) {
        Map<String,Object> m = new LinkedHashMap<>(); m.put("action",action);m.put("shard",name);m.put("totalProducts",total);m.put("productsMoved",moved);m.put("movementPercent",total==0?0.0:Math.round(moved*10000.0/total)/100.0);m.put("activeShards",router.getShards());m.put("distribution",distribution());return m;
    }
    public Map<String, Object> distribution() { Map<String,Object> m=new LinkedHashMap<>();for(String n:router.getShards())m.put(n,templates.get(n).count(new Query(),Product.class));return m; }
    public List<String> shardNames(){return router.getShards();}
    public ConsistentHashRouter router(){return router;}
    public List<Map<String,Object>> shardDetails(){List<Map<String,Object>> out=new ArrayList<>();for(ShardDefinition s:definitions.values()){Map<String,Object>d=new LinkedHashMap<>();d.put("name",s.getName());d.put("database",s.getDatabase());d.put("virtualNodes",s.getVirtualNodes());d.put("productCount",templates.get(s.getName()).count(new Query(),Product.class));out.add(d);}return out;}
    private List<Product> getCache(String key){try{String v=redis.opsForValue().get(key);return v==null?null:mapper.readValue(v,new TypeReference<List<Product>>(){});}catch(Exception e){return null;}}
    private void putCache(String key,List<Product> values){try{redis.opsForValue().set(key,mapper.writeValueAsString(values),Duration.ofMinutes(5));}catch(Exception ignored){}}
    private void invalidate(){try{Set<String> keys=redis.keys("product:*");if(keys!=null&&!keys.isEmpty())redis.delete(keys);}catch(Exception ignored){}}
}
