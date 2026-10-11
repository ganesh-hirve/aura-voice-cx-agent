package com.ganesh.aura.ai;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@FunctionalInterface
public interface SarvamHttpTransport {
    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
}
