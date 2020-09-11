package com.onvif.java.model;

import lombok.Data;

/**
 * @author zf
 */
@Data
public class DeviceInfo {

  private String manufacturer;
  private String model;
  private String firmwareVersion;
  private String serialNumber;
  private String hardwareId;

  public DeviceInfo(
      String manufacturer,
      String model,
      String firmwareVersion,
      String serialNumber,
      String hardwareId) {
    super();
    this.manufacturer = manufacturer;
    this.model = model;
    this.firmwareVersion = firmwareVersion;
    this.serialNumber = serialNumber;
    this.hardwareId = hardwareId;
  }

}
