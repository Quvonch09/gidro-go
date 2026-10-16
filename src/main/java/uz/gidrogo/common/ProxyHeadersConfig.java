package uz.gidrogo.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** app.security.trust-forwarded-headers ni {@link ClientIp} ga uzatadi (faqat Nginx ortida true qiling). */
@Configuration
public class ProxyHeadersConfig {

    public ProxyHeadersConfig(@Value("${app.security.trust-forwarded-headers:false}") boolean trust) {
        ClientIp.setTrustForwardedHeaders(trust);
    }
}
