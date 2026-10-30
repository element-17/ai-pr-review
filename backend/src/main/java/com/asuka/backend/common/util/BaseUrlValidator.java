package com.asuka.backend.common.util;

import com.asuka.backend.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * 出站模型 base URL 校验。
 *
 * <p>默认只允许 HTTPS、标准 443 端口和公网地址，拒绝 localhost、私网、链路本地、
 * 云元数据地址和重定向式路径。管理员显式开启 allowPrivate 时仅用于本地联调。
 */
public final class BaseUrlValidator {

    private BaseUrlValidator() {
    }

    public static void validate(String baseUrl, boolean allowPrivate) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(400, "base URL 不能为空");
        }
        String value = baseUrl.trim();
        if (value.length() > 500) {
            throw new BusinessException(400, "base URL 不能超过 500 个字符");
        }

        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "base URL 格式不正确");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !(allowPrivate && "http".equals(scheme))) {
            throw new BusinessException(400, "base URL 必须使用 HTTPS");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new BusinessException(400, "base URL 缺少域名");
        }
        if (uri.getUserInfo() != null) {
            throw new BusinessException(400, "base URL 不允许包含用户名或密码");
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new BusinessException(400, "base URL 不允许包含查询参数或锚点");
        }
        int port = uri.getPort();
        if (!allowPrivate && port != -1 && port != 443) {
            throw new BusinessException(400, "base URL 只允许 443 端口");
        }

        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith("/chat/completions") || path.endsWith("/messages")
                || path.endsWith("/v1/messages")) {
            throw new BusinessException(400, "base URL 只填 API 根地址，不要包含 /chat/completions 或 /messages");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowPrivate && isLocalHostName(host)) {
            throw new BusinessException(400, "base URL 不允许指向 localhost 或内网域名");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (Exception ex) {
            throw new BusinessException(400, "base URL 域名无法解析");
        }
        if (!allowPrivate) {
            for (InetAddress address : addresses) {
                if (isPrivateAddress(address)) {
                    throw new BusinessException(400, "base URL 不允许指向内网、链路本地或保留地址");
                }
            }
        }
    }

    private static boolean isLocalHostName(String host) {
        return "localhost".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal");
    }

    private static boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            int first = raw[0] & 0xff;
            int second = raw[1] & 0xff;
            // 100.64.0.0/10 运营商级 NAT，也不应作为模型地址
            return first == 0 || (first == 100 && second >= 64 && second <= 127);
        }
        if (raw.length == 16) {
            // fc00::/7 唯一本地地址、fe80::/10 链路本地地址
            return (raw[0] & 0xfe) == 0xfc
                    || ((raw[0] & 0xff) == 0xfe && (raw[1] & 0xc0) == 0x80);
        }
        return false;
    }
}
