package com.study.grabthisforme.auth;

import java.security.Principal;

public record StompPrincipal(String name, long userId) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
