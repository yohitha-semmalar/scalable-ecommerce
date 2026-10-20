package com.teamshop.product.controller;
import com.teamshop.product.model.Product;
import com.teamshop.product.service.ProductService;
import com.teamshop.product.sharding.ConsistentHashRouter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
@RestController @CrossOrigin(origins={"http://localhost:5173","http://localhost:3000"}) @RequestMapping("/api")
public class ProductController {
 private final ProductService products; private final ConsistentHashRouter router;
 public ProductController(ProductService p,ConsistentHashRouter r){products=p;router=r;}
 @GetMapping("/products") public Object all(@RequestParam(required=false) String q,@RequestParam(required=false) String category){return (q==null&&(category==null||category.isBlank()))?products.all():products.search(q,category);}
 @GetMapping("/products/{id}") public Product byId(@PathVariable String id){Product p=products.byId(id);if(p==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found");return p;}
 @GetMapping("/search") public Object search(@RequestParam(defaultValue="") String q,@RequestParam(required=false) String category){return products.search(q,category);}
 @PostMapping("/products") public Map<String,Object> addProduct(@RequestBody Product p){try{return products.addProduct(p);}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
 @GetMapping("/health") public Map<String,Object> health(){return Map.of("status","UP","service","product-search-service","shardCount",router.getShards().size(),"virtualNodesPerShard",router.getVirtualNodes());}
 @GetMapping("/shards") public Map<String,Object> shards(){return Map.of("shards",products.shardDetails(),"virtualNodesPerShard",router.getVirtualNodes());}
 @GetMapping("/shards/distribution") public Map<String,Object> distribution(){return products.distribution();}
 @PostMapping("/shards") public Map<String,Object> add(@RequestBody Map<String,String>b){String n=b.get("name");try{return products.addShard(n,b.get("uri"),b.get("database"));}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
 @DeleteMapping("/shards/{name}") public Map<String,Object> remove(@PathVariable String name){try{return products.removeShard(name);}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
}
