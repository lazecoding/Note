package com.codebase.javaSE.generics.example;

public class ShopImpl<T> implements Shop<T>{
    @Override
    public T get(T obj) {
        return obj;
    }
}