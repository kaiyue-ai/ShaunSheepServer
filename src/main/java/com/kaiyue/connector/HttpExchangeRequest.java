package com.kaiyue.connector;

import com.sun.net.httpserver.Headers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;

public interface HttpExchangeRequest {
    String getRequestMethod();

    URI getRequestURI();

    Headers getRequestHeaders();

    InetSocketAddress getRemoteAddress();

    InetSocketAddress getLocalAddress();

    byte[] getRequestBody() throws IOException;
}
