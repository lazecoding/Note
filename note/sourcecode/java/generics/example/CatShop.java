package com.codebase.javaSE.generics.example;

public interface CatShop<T extends Cat> {
    T get(T obj);
}
