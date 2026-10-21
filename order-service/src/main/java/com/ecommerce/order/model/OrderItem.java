package com.ecommerce.order.model;
import java.math.BigDecimal;
public class OrderItem {
 private String productId,productName; private int quantity; private BigDecimal unitPrice,subtotal;
 public OrderItem(){} public OrderItem(String p,String n,int q,BigDecimal u,BigDecimal s){productId=p;productName=n;quantity=q;unitPrice=u;subtotal=s;}
 public String getProductId(){return productId;} public void setProductId(String v){productId=v;}
 public String getProductName(){return productName;} public void setProductName(String v){productName=v;}
 public int getQuantity(){return quantity;} public void setQuantity(int v){quantity=v;}
 public BigDecimal getUnitPrice(){return unitPrice;} public void setUnitPrice(BigDecimal v){unitPrice=v;}
 public BigDecimal getSubtotal(){return subtotal;} public void setSubtotal(BigDecimal v){subtotal=v;}
}