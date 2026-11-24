package com.jingliu.chat;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;

final class SocksAuthentication extends Authenticator {
    private static final ThreadLocal<SocksAuthentication> active = new ThreadLocal<>();

    static {
        Authenticator.setDefault(new SocksAuthentication(null, null));
    }

    private final InetSocketAddress address;
    private final PasswordAuthentication credentials;

    SocksAuthentication(Proxy proxy, PasswordAuthentication credentials) {
        this.address = proxy == null ? null : (InetSocketAddress) proxy.address();
        this.credentials = credentials;
    }

    void enter() {
        active.set(this);
    }

    void leave() {
        if (active.get() == this) active.remove();
    }

    @Override protected PasswordAuthentication getPasswordAuthentication() {
        SocksAuthentication request = active.get();
        if (request == null || !"SOCKS5".equalsIgnoreCase(getRequestingProtocol())
                || request.address.getPort() != getRequestingPort()
                || !request.address.getHostString().equalsIgnoreCase(getRequestingHost())) return null;
        return request.credentials;
    }
}
