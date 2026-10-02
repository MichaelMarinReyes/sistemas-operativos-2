package com.sopes.backendproyectofinal.orders;

import java.util.List;

public record OrderPage(List<Order> items, int page, int size, long total) {}
