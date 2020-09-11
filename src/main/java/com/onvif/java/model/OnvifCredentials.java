package com.onvif.java.model;

import lombok.Data;

/**
 * onvif 连接信息
 * @author zf
 */
@Data
public class OnvifCredentials {
  /**
   * 92.168.xx.yy, or http://host[:port]
   */
  private String host;
  /**
   * admin
   */
  private String user;
  /**
   * secret
   */
  private String password;
  /**
   * "MediaProfile000"  If empty, will use first profile.
   */
  private String profile;

  public OnvifCredentials(String host, String user, String password, String profile) {
    this.host = host;
    this.user = user;
    this.password = password;
    this.profile = profile;
  }

  public String details() {
    return host + "," + user + "," + password + "," + profile;
  }
}
