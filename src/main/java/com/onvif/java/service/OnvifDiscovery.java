package com.onvif.java.service;


import java.net.URL;
import java.util.Collection;

/**
 * @author zf
 */
public class OnvifDiscovery {

  public static Collection<URL> discoverOnvifURLs() {
    return DeviceDiscovery.discoverWsDevicesAsUrls("^http$", ".*onvif.*");
  }
}
