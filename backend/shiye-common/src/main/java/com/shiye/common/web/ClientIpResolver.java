package com.shiye.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析真实客户端 IP。
 *
 * <p>只有直连对端在 {@code shiye.http.trusted-proxies} 内时才读取
 * {@code X-Forwarded-For}，并从右向左取第一个不受信任地址，防止客户端伪造
 * 最左侧的 XFF。默认只信任本机回环（本机 Caddy/nginx）。</p>
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private final List<Subnet> trustedProxies;

    public ClientIpResolver(
            @Value("${shiye.http.trusted-proxies:127.0.0.1/32,::1/128}")
            String trustedProxies) {
        this.trustedProxies = parseSubnets(trustedProxies);
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String remote = normalize(request.getRemoteAddr());
        if (remote == null) {
            return null;
        }
        if (!isTrusted(remote)) {
            // 直连对端不是受控代理时，完全忽略客户端传入的 XFF。
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || forwarded.length() > 1024) {
            return remote;
        }
        String[] parts = forwarded.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = normalize(parts[i].trim());
            if (candidate == null) {
                continue;
            }
            if (!isTrusted(candidate)) {
                return candidate;
            }
        }
        return remote;
    }

    private List<Subnet> parseSubnets(String configured) {
        List<Subnet> subnets = new ArrayList<>();
        if (configured == null) {
            return subnets;
        }
        for (String item : configured.split(",")) {
            String value = item.trim();
            if (value.isEmpty()) {
                continue;
            }
            Subnet subnet = parseSubnet(value);
            if (subnet == null) {
                log.warn("ignore invalid trusted proxy entry: {}", value);
            } else {
                subnets.add(subnet);
            }
        }
        return subnets;
    }

    private Subnet parseSubnet(String value) {
        String[] pieces = value.split("/", 2);
        String address = normalize(pieces[0].trim());
        if (address == null) {
            return null;
        }
        try {
            byte[] network = InetAddress.getByName(address).getAddress();
            int prefix = network.length * 8;
            if (pieces.length == 2) {
                prefix = Integer.parseInt(pieces[1].trim());
            }
            if (prefix < 0 || prefix > network.length * 8) {
                return null;
            }
            return new Subnet(network, prefix);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isTrusted(String address) {
        try {
            byte[] bytes = InetAddress.getByName(address).getAddress();
            for (Subnet subnet : trustedProxies) {
                if (matches(bytes, subnet)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean matches(byte[] address, Subnet subnet) {
        if (address.length != subnet.network().length) {
            return false;
        }
        int fullBytes = subnet.prefix() / 8;
        int remainder = subnet.prefix() % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (address[i] != subnet.network()[i]) {
                return false;
            }
        }
        if (remainder == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainder);
        return (address[fullBytes] & mask) == (subnet.network()[fullBytes] & mask);
    }

    /**
     * 归一化 IP 字面量：去掉 IPv6 方括号/zone，兼容 IPv4:port 和 IPv4-mapped IPv6。
     * 拒绝主机名，避免在请求路径上触发 DNS 解析。
     */
    private String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 0) {
                return null;
            }
            value = value.substring(1, end);
        } else if (value.indexOf(':') > 0
                && value.indexOf(':') == value.lastIndexOf(':')
                && value.indexOf('.') > 0) {
            value = value.substring(0, value.indexOf(':'));
        }
        int zone = value.indexOf('%');
        if (zone >= 0) {
            value = value.substring(0, zone);
        }
        if (!value.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            byte[] bytes = InetAddress.getByName(value).getAddress();
            if (bytes.length == 16 && isIpv4Mapped(bytes)) {
                byte[] ipv4 = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
                return InetAddress.getByAddress(ipv4).getHostAddress();
            }
            return InetAddress.getByName(value).getHostAddress();
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF;
    }

    private record Subnet(byte[] network, int prefix) {
    }
}
