package com.onvif.java.onvif;

import com.onvif.java.model.DeviceInfo;
import com.onvif.java.model.OnvifCredentials;
import com.onvif.java.service.OnvifDevice;
import com.onvif.java.utils.GetTestDevice;
import com.onvif.java.utils.OnvifUtils;
import org.onvif.ver10.device.wsdl.DeviceServiceCapabilities;
import org.onvif.ver10.events.wsdl.EventPortType;
import org.onvif.ver10.events.wsdl.GetEventProperties;
import org.onvif.ver10.events.wsdl.GetEventPropertiesResponse;
import org.onvif.ver10.media.wsdl.Media;
import org.onvif.ver10.schema.*;
import org.onvif.ver20.imaging.wsdl.ImagingPort;
import org.onvif.ver20.ptz.wsdl.Capabilities;
import org.onvif.ver20.ptz.wsdl.PTZ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.soap.SOAPException;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.lang.Object;

/** @author Brad Lowe */
public class TestDevice {
  private static final Logger LOG = LoggerFactory.getLogger(TestDevice.class);

  public static String testCamera(OnvifCredentials creds) throws SOAPException, IOException {
    URL u =
        creds.getHost().startsWith("http")
            ? new URL(creds.getHost())
            : new URL("http://" + creds.getHost());
    return testCamera(u, creds.getUser(), creds.getPassword());
  }

  static String sep = "\n";

  // This method returns information about an initialized OnvifDevice.
  // This could throw an uncaught SOAP or other error on some cameras...
  // Would accept Pull Requests on printing out additional information about devices.
  public static String inspect(OnvifDevice device) {
    String out = "";
    DeviceInfo info = device.getDeviceInfo();
    out += "DeviceInfo:" + sep + "\t" + info + sep;
    DeviceServiceCapabilities caps = device.getDevice().getServiceCapabilities();
    String sysCaps = OnvifUtils.format(caps);
    sysCaps = sysCaps.replace("],", "],\t\n");

    out += "\tgetServiceCapabilities: " + sysCaps + sep;
    // out += "\tgetServiceCapabilities.getSystem: " + OnvifUtils.format(caps.getSystem()) + sep;

    Media media = device.getMedia();

    media.getVideoSources();

    List<Profile> profiles = media.getProfiles();
    out += "Media Profiles: " + profiles.size() + sep;
    for (Profile profile : profiles) {
      String profileToken = profile.getToken();
      String format = OnvifUtils.format(profile);

      String channel = profileToken.substring(profileToken.length() - 3);
      StreamSetup streamSetup = new StreamSetup();
      Transport transport = new Transport();
      transport.setProtocol(TransportProtocol.UDP);
      streamSetup.setStream(StreamType.RTP_UNICAST);
      streamSetup.setTransport(transport);
//      String rtsp = device.getStreamUri(profileToken);
      String token = profile.getAudioEncoderConfiguration().getToken();
      String rtsp = media.getStreamUri(streamSetup, profileToken).getUri();
      out += "\tprofileToken: " + profileToken + sep;
      out += "\tProfile: " + profile.getName() + " token=" + token + "channel=" + channel + sep;
      out += "\t\tstream: " + rtsp + sep;
      out += "\t\tsnapshot: " + device.getSnapshotUri(profileToken) + sep;
      out += "\t\tdetails:" + format + sep;
    }

    try {
      List<VideoSource> videoSources = media.getVideoSources();
      out += "VideoSources: " + videoSources.size() + sep;
      for (VideoSource v : videoSources) out += "\t" + OnvifUtils.format(v) + sep;

      ImagingPort imaging = device.getImaging();
      if (imaging != null && videoSources.size() > 0) {
        String token = videoSources.get(0).getToken();

        out += "Imaging:" + token + sep;
        try {
          org.onvif.ver20.imaging.wsdl.Capabilities image_caps = imaging.getServiceCapabilities();
          out += "\tgetServiceCapabilities=" + OnvifUtils.format(image_caps) + sep;

          if (token != null) {
            out +=
                "\tgetImagingSettings="
                    + OnvifUtils.format(imaging.getImagingSettings(token))
                    + sep;
            out += "\tgetMoveOptions=" + OnvifUtils.format(imaging.getMoveOptions(token)) + sep;
            out += "\tgetStatus=" + OnvifUtils.format(imaging.getStatus(token)) + sep;
            out += "\tgetOptions=" + OnvifUtils.format(imaging.getOptions(token)) + sep;
          }
        } catch (Throwable th) {
          out += "Imaging unavailable:" + th.getMessage() + sep;
        }
      }
    } catch (Throwable th) {
      // this can fail if the device doesn't support video sources.
      out += "VideoSources: " + th.getMessage() + sep;
    }
    try {
      // This may throw a SoapFaultException with the message "This device does not support audio"
      List<AudioSource> audioSources = media.getAudioSources();
      out += "AudioSources: " + audioSources.size() + sep;
      for (AudioSource a : audioSources) out += "\t" + OnvifUtils.format(a) + sep;
    } catch (Throwable th) {
      out += "AudioSources Unavailable: " + th.getMessage() + sep;
    }

    try {
      EventPortType events = device.getEvents();
      if (events != null) {
        out += "Events:" + sep;
        out +=
            "\tgetServiceCapabilities=" + OnvifUtils.format(events.getServiceCapabilities()) + sep;

        GetEventProperties getEventProperties = new GetEventProperties();
        GetEventPropertiesResponse getEventPropertiesResp =
            events.getEventProperties(getEventProperties);
        out += "\tMessageContentFilterDialects:" + sep;
        for (String f : getEventPropertiesResp.getMessageContentFilterDialect())
          out += ("\t\t" + f + sep);
        out += "\tTopicExpressionDialects:" + sep;
        for (String f : getEventPropertiesResp.getTopicExpressionDialect())
          out += ("\t\t" + f + sep);

        out += "\tTopics:" + sep;
        StringBuffer tree = new StringBuffer();
        for (Object object : getEventPropertiesResp.getTopicSet().getAny()) {
          Element e = (Element) object;
          printTree(e, e.getNodeName(), tree);
          // WsNotificationTest.printTree(e, e.getNodeName());
        }
        out += tree;
      }
    } catch (Throwable th) {
      out += "Events Unavailable: " + th.getMessage() + sep;
    }
    PTZ ptz = device.getPtz();
    if (ptz != null) {

      String profileToken = profiles.get(0).getToken();
      try {
        Capabilities ptz_caps = ptz.getServiceCapabilities();
        out += "PTZ:" + sep;
        out += "\tgetServiceCapabilities=" + OnvifUtils.format(ptz_caps) + sep;
        PTZStatus s = ptz.getStatus(profileToken);
        out += "\tgetStatus=" + OnvifUtils.format(s) + sep;
        // out += "ptz.getConfiguration=" + ptz.getConfiguration(profileToken) + sep;
        List<PTZPreset> presets = ptz.getPresets(profileToken);
        if (presets != null && !presets.isEmpty()) {
          out += "\tPresets:" + presets.size() + sep;
          for (PTZPreset p : presets) out += "\t\t" + OnvifUtils.format(p) + sep;
        }
      } catch (Throwable th) {
        out += "PTZ: Unavailable" + th.getMessage() + sep;
      }
    }

    return out;
  }

  public static void printTree(Node node, String name, StringBuffer buffer) {

    if (node.hasChildNodes()) {
      NodeList nodes = node.getChildNodes();
      for (int i = 0; i < nodes.getLength(); i++) {
        Node n = nodes.item(i);
        printTree(n, name + " - " + n.getNodeName(), buffer);
      }
    } else {
      buffer.append("\t\t" + name + " - " + node.getNodeName() + "\n");
    }
  }

  public static String testCamera(URL url, String user, String password)
      throws SOAPException, IOException {
    LOG.info("Testing camera:" + url);
    OnvifDevice device = new OnvifDevice(url, user, password);
    return inspect(device);
  }

  public static void main(String[] args) {
    OnvifCredentials creds = GetTestDevice.getOnvifCredentials(args);
    try {
      // OnvifDevice.setVerbose(true);
      String out = testCamera(creds);

      LOG.info("\n" + out + "\n");
    } catch (Throwable th) {
      LOG.error("Failed for " + creds, th);
      th.printStackTrace();
    }
  }
}
