package com.kaiyue.connector;


import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

public class HttpExchangeAdapter implements HttpExchangeRequest, HttpExchangeResponse {

    final HttpExchange exchange;
    byte[] requestBodyData;

    public HttpExchangeAdapter(HttpExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
        this.exchange.sendResponseHeaders(rCode, responseLength);
    }

    @Override
    public OutputStream getResponseBody() {
        return this.exchange.getResponseBody();
    }

    @Override
    public String getRequestMethod() {
        return this.exchange.getRequestMethod();
    }

    @Override
    public URI getRequestURI() {
        return this.exchange.getRequestURI();
    }

    @Override
    public Headers getRequestHeaders() {
        return this.exchange.getRequestHeaders();
    }

    @Override
    public Headers getResponseHeaders() {
        return this.exchange.getResponseHeaders();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return this.exchange.getRemoteAddress();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return this.exchange.getLocalAddress();
    }

    @Override
    public byte[] getRequestBody() throws IOException {
        if (this.requestBodyData == null) {
            try (InputStream input = this.exchange.getRequestBody()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int bytesRead;
                while ((bytesRead = input.read(chunk)) != -1) {
                    buffer.write(chunk, 0, bytesRead);
                }
                this.requestBodyData = buffer.toByteArray();
            }
        }
        return this.requestBodyData;
    }
}
