package com.codebase.javaSE.generics.example;

public class Storekeeper {
    public Shop open(Shop<? super Cat> shop){
        return shop;
    }

    public Shop close (Shop<? extends Dog> shop){
        return shop;
    }
}
