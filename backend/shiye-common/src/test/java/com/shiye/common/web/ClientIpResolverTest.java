package com.shiye.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClientIpResolverTest {

    private final ClientIpResolver resolver =
            new ClientIpResolver("127.0.0.1/32,::1/128,10.0.0.0/8");

    @Test
    void ignoresForwardedHeaderWhenPeerIsNotTrusted() {
        MockHttpServletRequest request = request("198.51.100.10");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertEquals("198.51.100.10", resolver.resolve(request));
    }

    @Test
    void takesRightmostUntrustedAddressFromTrustedProxy() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.7");

        assertEquals("203.0.113.7", resolver.resolve(request));
    }

    @Test
    void skipsTrustedProxyHops() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.1.2.3");

        assertEquals("203.0.113.7", resolver.resolve(request));
    }

    @Test
    void fallsBackToPeerWhenForwardedEntriesAreInvalid() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "unknown, not-an-ip");

        assertEquals("127.0.0.1", resolver.resolve(request));
    }

    @Test
    void normalizesIpv4MappedIpv6() {
        MockHttpServletRequest request = request("::ffff:127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertEquals("203.0.113.7", resolver.resolve(request));
    }

    @Test
    void handlesIpv6LoopbackAndBracketPort() {
        MockHttpServletRequest request = request("0:0:0:0:0:0:0:1");
        request.addHeader("X-Forwarded-For", "[2001:db8::1]:443");

        assertEquals("2001:db8:0:0:0:0:0:1", resolver.resolve(request));
    }

    @Test
    void returnsNullWithoutRequest() {
        assertNull(resolver.resolve(null));
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
