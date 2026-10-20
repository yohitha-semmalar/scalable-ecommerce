package com.teamshop.product.sharding;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConsistentHashRouter {
    private final TreeMap<Long,String> ring = new TreeMap<>();
    private final Set<String> shards = new LinkedHashSet<>();
    private final int virtualNodes;
    @Autowired
    public ConsistentHashRouter(@Value("${product.sharding.virtual-nodes:100}") int virtualNodes) { this.virtualNodes=Math.max(1,virtualNodes); }
    public ConsistentHashRouter(int initialShards, int virtualNodes) { this.virtualNodes=Math.max(1,virtualNodes); for(int i=1;i<=Math.max(1,initialShards);i++) addShard("shard"+i); }
    public synchronized void reset(Collection<String> names) { ring.clear(); shards.clear(); for(String name:names) addShard(name); }
    public synchronized void addShard(String name) { if(shards.contains(name)) return; shards.add(name); for(int i=0;i<virtualNodes;i++) ring.put(hash(name+"-vn"+i),name); }
    public synchronized boolean removeShard(String name) { if(!shards.contains(name)||shards.size()<=1)return false; shards.remove(name); ring.entrySet().removeIf(e->e.getValue().equals(name)); return true; }
    public synchronized String getShard(String key) { if(ring.isEmpty()) throw new IllegalStateException("No active shards"); Map.Entry<Long,String> e=ring.ceilingEntry(hash(key)); return e==null?ring.firstEntry().getValue():e.getValue(); }
    public synchronized List<String> getShards(){return new ArrayList<>(shards);} public int getVirtualNodes(){return virtualNodes;}
    private long hash(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));return ByteBuffer.wrap(b,0,8).getLong()&Long.MAX_VALUE;}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
