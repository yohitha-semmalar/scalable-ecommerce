package com.teamshop.product.sharding;

public class ShardDefinition {
    private String name; private String uri; private String database; private int virtualNodes = 100; private boolean active = true;
    public ShardDefinition() {}
    public ShardDefinition(String name, String uri, String database, int virtualNodes, boolean active) { this.name=name; this.uri=uri; this.database=database; this.virtualNodes=virtualNodes; this.active=active; }
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getUri(){return uri;} public void setUri(String v){uri=v;}
    public String getDatabase(){return database;} public void setDatabase(String v){database=v;}
    public int getVirtualNodes(){return virtualNodes;} public void setVirtualNodes(int v){virtualNodes=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
