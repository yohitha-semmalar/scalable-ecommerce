package com.ecommerce.order.service;

import com.ecommerce.order.dto.*;
import com.ecommerce.order.model.*;
import com.ecommerce.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository repo;

    @Mock
    CartClient carts;

    @Mock
    ProductClient products;

    @InjectMocks
    OrderService service;

    @Test
    void createsMultipleItemsAndCalculatesTotal() {
        when(carts.getCart("Bearer x")).thenReturn(
            new CartResponse(
                List.of(
                    new CartResponse.CartItem("P1", "Mouse", 2, null, null),
                    new CartResponse.CartItem("P2", "Keys", 1, null, null)
                ),
                null
            )
        );

        when(products.getProduct("P1")).thenReturn(
            new ProductResponse("P1", "Mouse", new BigDecimal("799"), true, 10)
        );

        when(products.getProduct("P2")).thenReturn(
            new ProductResponse("P2", "Keys", new BigDecimal("1200"), true, 10)
        );

        when(repo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId("order-1");
            return o;
        });

        OrderResponse out = service.create("user-1", "Bearer x", "key-1");

        assertEquals(new BigDecimal("2798.00"), out.totalAmount());
        assertEquals(2, out.items().size());
        assertEquals(new BigDecimal("1598.00"), out.items().get(0).subtotal());

        verify(carts).clearCart("Bearer x");
    }

    @Test
    void rejectsEmptyCart() {
        when(carts.getCart("Bearer x")).thenReturn(
            new CartResponse(List.of(), BigDecimal.ZERO)
        );

        ServiceException e = assertThrows(
            ServiceException.class,
            () -> service.create("u", "Bearer x", null)
        );

        assertEquals(409, e.getStatus().value());
        verify(repo, never()).save(any());
        verify(carts, never()).clearCart(any());
    }

    @Test
    void rejectsInvalidQuantity() {
        when(carts.getCart("Bearer x")).thenReturn(
            new CartResponse(
                List.of(new CartResponse.CartItem("P1", "Mouse", 0, null, null)),
                null
            )
        );

        ServiceException e = assertThrows(
            ServiceException.class,
            () -> service.create("u", "Bearer x", null)
        );

        assertEquals("INVALID_CART_ITEM", e.getCode());
        verify(repo, never()).save(any());
    }

    @Test
    void cartClearFailureStillReturnsPersistedOrder() {
        when(carts.getCart("Bearer x")).thenReturn(
            new CartResponse(
                List.of(new CartResponse.CartItem("P1", "Mouse", 1, null, null)),
                null
            )
        );

        when(products.getProduct("P1")).thenReturn(
            new ProductResponse("P1", "Mouse", new BigDecimal("10"), true, null)
        );

        when(repo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId("order-1");
            return o;
        });

        doThrow(
            new ServiceException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "CART_CLEAR_FAILED",
                "down"
            )
        ).when(carts).clearCart("Bearer x");

        OrderResponse out = service.create("u", "Bearer x", "k");

        assertFalse(out.cartCleared());
        assertTrue(out.checkoutMessage().contains("cart clearing failed"));
        verify(repo, times(1)).save(any());
    }
}