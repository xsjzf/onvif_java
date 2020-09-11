package com.onvif.java.service;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.transport.http.HTTPConduit;

import javax.net.ssl.*;
import javax.xml.ws.BindingProvider;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * @author zf
 */
public class NaiveSSLHelper {
  public static void makeWebServiceClientTrustEveryone(Object webServicePort) {
    if (webServicePort instanceof BindingProvider) {
      BindingProvider bp = (BindingProvider) webServicePort;
      Map requestContext = bp.getRequestContext();
      requestContext.put(JAXWS_SSL_SOCKET_FACTORY, getTrustingSSLSocketFactory());
      requestContext.put(JAXWS_HOSTNAME_VERIFIER, new NaiveHostnameVerifier());
    } else {
      throw new IllegalArgumentException(
          "Web service port "
              + webServicePort.getClass().getName()
              + " does not implement "
              + BindingProvider.class.getName());
    }
  }

  public static SSLSocketFactory getTrustingSSLSocketFactory() {
    return SSLSocketFactoryHolder.INSTANCE;
  }

  private static SSLSocketFactory createSSLSocketFactory() {
    TrustManager[] trustManagers = new TrustManager[] {new NaiveTrustManager()};
    SSLContext sslContext;
    try {
      sslContext = SSLContext.getInstance("TLS");
      sslContext.init(new KeyManager[0], trustManagers, new SecureRandom());
      return sslContext.getSocketFactory();
    } catch (GeneralSecurityException e) {
      return null;
    }
  }

  public static void makeCxfWebServiceClientTrustEveryone(HTTPConduit http) {
    TrustManager[] trustManagers = new TrustManager[] {new NaiveTrustManager()};
    TLSClientParameters tlsParams = new TLSClientParameters();
    tlsParams.setSecureSocketProtocol("TLS");
    tlsParams.setKeyManagers(new KeyManager[0]);
    tlsParams.setTrustManagers(trustManagers);
    tlsParams.setDisableCNCheck(true);
    http.setTlsClientParameters(tlsParams);
  }

  private interface SSLSocketFactoryHolder {
    SSLSocketFactory INSTANCE = createSSLSocketFactory();
  }

  private static class NaiveHostnameVerifier implements HostnameVerifier {
    @Override
    public boolean verify(String hostName, SSLSession session) {
      return true;
    }
  }

  private static class NaiveTrustManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] certs, String authType)
        throws CertificateException {}

    @Override
    public void checkServerTrusted(X509Certificate[] certs, String authType)
        throws CertificateException {}

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }

  private static final String JAXWS_HOSTNAME_VERIFIER =
      "com.sun.xml.internal.ws.transport.https.client.hostname.verifier";
  private static final String JAXWS_SSL_SOCKET_FACTORY =
      "com.sun.xml.internal.ws.transport.https.client.SSLSocketFactory";
}
