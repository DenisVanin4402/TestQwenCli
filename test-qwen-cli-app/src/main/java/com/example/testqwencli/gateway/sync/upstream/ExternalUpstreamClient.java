package com.example.testqwencli.gateway.sync.upstream;

public interface ExternalUpstreamClient {

	ExternalUpstreamResponse call(ExternalUpstreamRequest request);
}
