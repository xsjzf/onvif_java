package com.onvif.java.service;

import com.onvif.java.model.DeviceInfo;
import org.apache.cxf.binding.soap.Soap12;
import org.apache.cxf.binding.soap.SoapBindingConfiguration;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.onvif.ver10.device.wsdl.Device;
import org.onvif.ver10.device.wsdl.DeviceService;
import org.onvif.ver10.events.wsdl.EventPortType;
import org.onvif.ver10.events.wsdl.EventService;
import org.onvif.ver10.media.wsdl.Media;
import org.onvif.ver10.media.wsdl.MediaService;
import org.onvif.ver10.recording.wsdl.RecordingPort;
import org.onvif.ver10.recording.wsdl.RecordingService;
import org.onvif.ver10.replay.wsdl.ReplayPort;
import org.onvif.ver10.replay.wsdl.ReplayService;
import org.onvif.ver10.schema.*;
import org.onvif.ver10.search.wsdl.SearchPort;
import org.onvif.ver10.search.wsdl.SearchService;
import org.onvif.ver20.imaging.wsdl.ImagingPort;
import org.onvif.ver20.imaging.wsdl.ImagingService;
import org.onvif.ver20.ptz.wsdl.PTZ;
import org.onvif.ver20.ptz.wsdl.PtzService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.soap.SOAPException;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * @author zf
 */
public class OnvifDevice {
  private static final Logger logger = LoggerFactory.getLogger(OnvifDevice.class);
  private static final String DEVICE_SERVICE = "/onvif/device_service";

  /**
   * http://host:port, https://host, http://host, http://ip_address
   */
  private final URL url;

  private String user;
  private String password;

  private Device device;
  private Media media;
  private PTZ ptz;
  private ImagingPort imaging;
  private ReplayPort replayPort;
  private RecordingPort recordingPort;
  private EventPortType events;
  private SearchPort searchPort;

  /**
   * 启用/禁用SOAP消息的日志记录
   */
  private static boolean verbose = false;
  final SimpleSecurityHandler securityHandler;

  private static URL cleanURL(URL u) throws ConnectException {
    if (u == null) {
      throw new ConnectException("null url not allowed");
    }
    String f = u.getFile();
    if (!f.isEmpty()) {
      String out = u.toString().replace(f, "");
      try {
        return new URL(out);
      } catch (MalformedURLException e) {
        throw new ConnectException("MalformedURLException " + u);
      }
    }

    return u;
  }


  /**
   * @param url http://host 或者 http://host:port 或者 https://host 或者 https://host:port
   * @param user     需要登录的用户名, 或者 "" for none
   * @param password 需要登录的密码, 或者 "" for none
   */
  public OnvifDevice(URL url, String user, String password) throws ConnectException, SOAPException {
    this.url = cleanURL(url);
    this.user = user;
    this.password = password;
    securityHandler =
        !user.isEmpty() && !password.isEmpty() ? new SimpleSecurityHandler(user, password) : null;
    init();
  }

  /**
   * 初始化Onvif设备，例如具有登录数据的网络视频传输器（NVT）
   *
   * @param deviceIp 设备的IP地址或主机名，您也可以添加端口
   * @param user 您需要登录的用户名
   * @param password 用户密码登录
   * @throws ConnectException 如果设备不可访问或无效，并且*无法回答SOAP消息，则会引发异常
   * @throws SOAPException
   */
  public OnvifDevice(String deviceIp, String user, String password)
      throws ConnectException, SOAPException, MalformedURLException {
    this(
        deviceIp.startsWith("http") ? new URL(deviceIp) : new URL("http://" + deviceIp),
        user,
        password);
  }

  /**
   * 初始化Onvif设备，例如具有登录数据的网络视频传输器（NVT）。
   *
   * @param hostIp 设备的IP地址，您还可以添加端口，但可以使用noch协议（例如* http：//）
   * @throws ConnectException 如果设备不可访问或无效，并且*无法回答SOAP消息，则会引发异常
   * @throws SOAPException
   */
  public OnvifDevice(String hostIp) throws ConnectException, SOAPException, MalformedURLException {
    this(hostIp, null, null);
  }

  /**
   * 如果给定的IP是*代理，则初始化用于SOAP消息并获取内部IP的地址。
   *
   * @throws ConnectException Get thrown if device doesn't give answers to GetCapabilities()
   * @throws SOAPException
   */
  protected void init() throws ConnectException, SOAPException {

    DeviceService deviceService = new DeviceService(null, DeviceService.SERVICE);

    BindingProvider deviceServicePort = (BindingProvider) deviceService.getDevicePort();
    this.device =
        getServiceProxy(deviceServicePort, url.toString() + DEVICE_SERVICE).create(Device.class);

    // resetSystemDateAndTime();		// don't modify the camera in a constructor.. :)
    Capabilities capabilities = this.device.getCapabilities(Arrays.asList(CapabilityCategory.ALL));
    if (capabilities == null) {
      throw new ConnectException("Capabilities not reachable.");
    }

    if (capabilities.getMedia() != null && capabilities.getMedia().getXAddr() != null) {
      this.media = new MediaService().getMediaPort();
      this.media =
          getServiceProxy((BindingProvider) media, capabilities.getMedia().getXAddr())
              .create(Media.class);
    }

    if (capabilities.getPTZ() != null && capabilities.getPTZ().getXAddr() != null) {
      this.ptz = new PtzService().getPtzPort();
      this.ptz =
          getServiceProxy((BindingProvider) ptz, capabilities.getPTZ().getXAddr())
              .create(PTZ.class);
    }

    if (capabilities.getImaging() != null && capabilities.getImaging().getXAddr() != null) {
      this.imaging = new ImagingService().getImagingPort();
      this.imaging =
          getServiceProxy((BindingProvider) imaging, capabilities.getImaging().getXAddr())
              .create(ImagingPort.class);
    }

    if (capabilities.getEvents() != null && capabilities.getEvents().getXAddr() != null) {
      this.events = new EventService().getEventPort();
      this.events =
          getServiceProxy((BindingProvider) events, capabilities.getEvents().getXAddr())
              .create(EventPortType.class);
    }

    if (capabilities.getExtension().getReplay() != null && capabilities.getExtension().getReplay().getXAddr() != null) {
      this.replayPort = new ReplayService().getReplayPort();
      this.replayPort =
              getServiceProxy((BindingProvider) replayPort, capabilities.getExtension().getReplay().getXAddr())
                      .create(ReplayPort.class);
    }

    if (capabilities.getExtension().getRecording() != null && capabilities.getExtension().getRecording().getXAddr() != null) {
      this.recordingPort = new RecordingService().getRecordingPort();
      this.recordingPort =
              getServiceProxy((BindingProvider) recordingPort, capabilities.getExtension().getRecording().getXAddr())
                      .create(RecordingPort.class);
    }
    if (capabilities.getExtension().getSearch() != null && capabilities.getExtension().getSearch().getXAddr() != null) {
      this.searchPort = new SearchService().getSearchPort();
      this.searchPort =
              getServiceProxy((BindingProvider) searchPort, capabilities.getExtension().getSearch().getXAddr())
                      .create(SearchPort.class);
    }
  }

  public JaxWsProxyFactoryBean getServiceProxy(BindingProvider servicePort, String serviceAddr) {

    JaxWsProxyFactoryBean proxyFactory = new JaxWsProxyFactoryBean();
    proxyFactory.getHandlers();

    if (serviceAddr != null) {
      proxyFactory.setAddress(serviceAddr);
    }
    proxyFactory.setServiceClass(servicePort.getClass());

    SoapBindingConfiguration config = new SoapBindingConfiguration();

    config.setVersion(Soap12.getInstance());
    proxyFactory.setBindingConfig(config);
    Client deviceClient = ClientProxy.getClient(servicePort);

    if (verbose) {
      // 日志记录拦截器
      proxyFactory.getOutInterceptors().add(new LoggingOutInterceptor());
      proxyFactory.getInInterceptors().add(new LoggingInInterceptor());
    }

    HTTPConduit http = (HTTPConduit) deviceClient.getConduit();
    if (securityHandler != null) {
      proxyFactory.getHandlers().add(securityHandler);
    }
    HTTPClientPolicy httpClientPolicy = http.getClient();
    httpClientPolicy.setConnectionTimeout(36000);
    httpClientPolicy.setReceiveTimeout(32000);
    httpClientPolicy.setAllowChunking(false);

    return proxyFactory;
  }

  public void resetSystemDateAndTime() {
    Calendar calendar = Calendar.getInstance();
    Date currentDate = new Date();
    boolean daylightSavings = calendar.getTimeZone().inDaylightTime(currentDate);
    org.onvif.ver10.schema.TimeZone timeZone = new org.onvif.ver10.schema.TimeZone();
    timeZone.setTZ(displayTimeZone(calendar.getTimeZone()));
    Time time = new Time();
    time.setHour(calendar.get(Calendar.HOUR_OF_DAY));
    time.setMinute(calendar.get(Calendar.MINUTE));
    time.setSecond(calendar.get(Calendar.SECOND));
    org.onvif.ver10.schema.Date date = new org.onvif.ver10.schema.Date();
    date.setYear(calendar.get(Calendar.YEAR));
    date.setMonth(calendar.get(Calendar.MONTH) + 1);
    date.setDay(calendar.get(Calendar.DAY_OF_MONTH));
    DateTime utcDateTime = new DateTime();
    utcDateTime.setDate(date);
    utcDateTime.setTime(time);
    device.setSystemDateAndTime(SetDateTimeType.MANUAL, daylightSavings, timeZone, utcDateTime);
  }

  private static String displayTimeZone(TimeZone tz) {

    long hours = TimeUnit.MILLISECONDS.toHours(tz.getRawOffset());
    long minutes =
        TimeUnit.MILLISECONDS.toMinutes(tz.getRawOffset()) - TimeUnit.HOURS.toMinutes(hours);
    // avoid -4:-30 issue
    minutes = Math.abs(minutes);

    String result = "";
    if (hours > 0) {
      result = String.format("GMT+%02d:%02d", hours, minutes);
    } else {
      result = String.format("GMT%02d:%02d", hours, minutes);
    }

    return result;
  }

  public DeviceInfo getDeviceInfo() {
    Holder<String> manufacturer = new Holder<>();
    Holder<String> model = new Holder<>();
    Holder<String> firmwareVersion = new Holder<>();
    Holder<String> serialNumber = new Holder<>();
    Holder<String> hardwareId = new Holder<>();
    device.getDeviceInformation(manufacturer, model, firmwareVersion, serialNumber, hardwareId);
    return new DeviceInfo(
        manufacturer.value,
        model.value,
        firmwareVersion.value,
        serialNumber.value,
        hardwareId.value);
  }


  /**
   * 获取快照rtsp地址
   * @param profileToken
   * @return http://host[:port]/path_for_snapshot
   */
  public String getSnapshotUri(String profileToken) {
    MediaUri sceenshotUri = media.getSnapshotUri(profileToken);
    if (sceenshotUri != null) {
      return sceenshotUri.getUri();
    }
    return "";
  }

  /**
   *  snapshot uri 的index
   * @param index
   * @return
   */
  public String getSnapshotUri(int index) {
    if (media.getProfiles().size() >= index) {
      return getSnapshotUri(media.getProfiles().get(index).getToken());
    }
    return "";
  }

  public String getStreamUri(int index) {
    return getStreamUri(media.getProfiles().get(index).getToken(), TransportProtocol.RTSP);
  }

  /**
   * 获取实时流的rtsp地址
   * @param profileToken
   * @param protocol
   * @return rtsp://host[:port]/path_for_rtsp
   */
  public String getStreamUri(String profileToken, TransportProtocol protocol) {
    StreamSetup streamSetup = new StreamSetup();
    Transport t = new Transport();
    t.setProtocol(protocol);
    streamSetup.setTransport(t);
    streamSetup.setStream(StreamType.RTP_UNICAST);
    MediaUri rtsp = media.getStreamUri(streamSetup, profileToken);
    return rtsp != null ? rtsp.getUri() : "";
  }

  /**
   * 获取历史流rtsp地址
   * @param device
   * @param profileToken
   * @param protocol
   * @return
   */
  public String getReplayUri(OnvifDevice device, String profileToken, TransportProtocol protocol) {
    StreamSetup streamSetup = new StreamSetup();
    Transport t = new Transport();
    t.setProtocol(protocol);
    streamSetup.setTransport(t);
    streamSetup.setStream(StreamType.RTP_UNICAST);
    ReplayPort replayPort = device.getReplayPort();
    String replayUri = replayPort.getReplayUri(streamSetup, profileToken);
    org.onvif.ver10.replay.wsdl.Capabilities capabilities = replayPort.getServiceCapabilities();
    return replayUri != null ? replayUri : "";
  }

  public static boolean isVerbose() {
    return verbose;
  }

  public static void setVerbose(boolean verbose) {
    OnvifDevice.verbose = verbose;
  }

  public String getHostname() {
    return device.getHostname().getName();
  }

  public String reboot() throws ConnectException, SOAPException {
    return device.systemReboot();
  }

  public String getSnapshotUri() {
    return getSnapshotUri(0);
  }

  public String getStreamUri() {
    return getStreamUri(0);
  }
  /**
   * 用于基本设备和给定Onvif设备的请求
   *
   */
  public Device getDevice() {
    return device;
  }

  public PTZ getPtz() {
    return ptz;
  }

  public Media getMedia() {
    return media;
  }

  public ImagingPort getImaging() {
    return imaging;
  }

  public EventPortType getEvents() {
    return events;
  }

  public ReplayPort getReplayPort() {
    return replayPort;
  }

  public void setReplayPort(ReplayPort replayPort) {
    this.replayPort = replayPort;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public SearchPort getSearchPort() {
    return searchPort;
  }

  public void setSearchPort(SearchPort searchPort) {
    this.searchPort = searchPort;
  }

  public RecordingPort getRecordingPort() {
    return recordingPort;
  }

  public void setRecordingPort(RecordingPort recordingPort) {
    this.recordingPort = recordingPort;
  }

  public DateTime getDate() {
    return device.getSystemDateAndTime().getLocalDateTime();
  }
}
