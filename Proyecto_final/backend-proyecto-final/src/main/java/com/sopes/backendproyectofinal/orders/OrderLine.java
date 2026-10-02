package com.sopes.backendproyectofinal.orders;

import com.sopes.backendproyectofinal.catalog.MerchandiseType;

public record OrderLine(String productId, String productName, MerchandiseType merchandiseType, int quantity) {}
