package com.nklmish.cs.catalogs.model;

import lombok.Value;

import java.util.List;

@Value
public class Catalog {
    private int id;
    private String price;
    private List<Product> products;
}
