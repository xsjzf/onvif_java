package com.onvif.java.utils;

import com.onvif.java.common.RrException;
import com.onvif.java.model.OnvifCredentials;
import com.onvif.java.service.OnvifDevice;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameGrabber;
import org.onvif.ver10.schema.GetRecordingsResponseItem;
import org.onvif.ver10.schema.Profile;
import org.onvif.ver10.schema.TransportProtocol;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.xml.soap.SOAPException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;

/**
 * @program: javaOnvif
 * @description: 获取rtsp地址
 * @author: zf
 * @create: 2020-09-08 10:50
 **/
@Component
public class MediaUtils {

    @Autowired
    ThreadPoolTaskExecutor taskExecutor;
    /**
     * 视频帧率
     */
    public static final int FRAME_RATE = 25;
    /**
     * 视频宽度
     */
    public static final int FRAME_WIDTH = 480;
    /**
     * 视频高度
     */
    public static final int FRAME_HEIGHT = 270;
    /**
     * 流编码格式
     */
    public static final int VIDEO_CODEC = avcodec.AV_CODEC_ID_H264;
    /**
     * 编码延时 zerolatency(零延迟)
     */
    public static final String TUNE = "zerolatency";
    /**
     * 编码速度 ultrafast(极快)
     */
    public static final String PRESET = "ultrafast";
    /**
     * 录制的视频格式 flv(rtmp格式) h264(udp格式) mpegts(未压缩的udp) rawvideo
     */
    public static final String FORMAT = "h264";
    /**
     * 比特率
     */
    public static final int VIDEO_BITRATE = 200000;

    private static FFmpegFrameGrabber grabber = null;
    private static FFmpegFrameRecorder recorder = null;

    /**
     * 选择视频源
     * @param src
     * @author eguid
     * @throws FrameGrabber.Exception
     */
    public MediaUtils from(String src) throws FrameGrabber.Exception {
        // 采集/抓取器
        grabber = MediaUtils.createGrabber(src);
        // 开始之后ffmpeg会采集视频信息
        grabber.start();
        return this;
    }

    /**
     * 选择输出
     * @param out
     * @author eguid
     * @throws IOException
     */
    public MediaUtils to(String out) throws IOException {
        //增加udp的缓存大小，解决延时卡顿?overrun_nonfatal=1&fifo_size=50000000
        String suffix = "?overrun_nonfatal=1&fifo_size=50000000";
        if (!out.contains(suffix)){
            out = out + suffix;
        }
        // 流媒体输出地址，分辨率（长，高），是否录制音频（0:不录制/1:录制） ?
        recorder = new FFmpegFrameRecorder(out, MediaUtils.FRAME_WIDTH, MediaUtils.FRAME_HEIGHT, 0);
        // 直播流格式
        recorder.setVideoCodec(VIDEO_CODEC);
        // 降低编码延时
        recorder.setVideoOption("tune", TUNE);
        recorder.setMaxDelay(500);
        recorder.setGopSize(10);
        // 提升编码速度
        recorder.setVideoOption("preset", PRESET);
        // 录制的视频格式 flv(rtmp格式) h264(udp格式) mpegts(未压缩的udp) rawvideo(原流)
        recorder.setFormat(FORMAT);
        // 帧数
        double frameLength = grabber.getLengthInFrames();
        long frameTime = grabber.getLengthInTime();
        double v = frameLength * 1000 * 1000 / frameTime;
        recorder.setFrameRate(v);
        //百度翻译的比特率，默认400000
        recorder.setVideoBitrate(VIDEO_BITRATE);
        // yuv420p
        recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
        recorder.start(grabber.getFormatContext());
        return this;
    }

    /**
     * 转封装
     * @author eguid
     * @throws IOException
     */
    public MediaUtils go() throws IOException {
        System.out.println("开始推送...");
        //采集或推流导致的错误次数
        long errIndex = 0;
        // 释放探测时缓存下来的数据帧，避免pts初始值不为0导致画面延时
        grabber.flush();
        //连续五次没有采集到帧则认为视频采集结束，程序错误次数超过1次即中断程序
        for(int noFrameIndex = 0; noFrameIndex < 10 || errIndex > 1;) {
            AVPacket pkt;
            try {
                //没有解码的音视频帧
                pkt = grabber.grabPacket();
                if(pkt == null || pkt.size() <= 0 || pkt.data() == null) {
                    //空包记录次数跳过
                    noFrameIndex ++;
                    continue;
                }
                //不需要编码直接把音视频帧推出去
                //如果失败err_index自增1
                errIndex += (recorder.recordPacket(pkt) ? 0 : 1);
                av_packet_unref(pkt);
            //推流失败
            } catch (IOException e) {
                errIndex++;
            }
        }
        return this;
    }

    /**
     * 构造视频抓取器
     * @param rtsp 拉流地址
     * @return
     */
    public static FFmpegFrameGrabber createGrabber(String rtsp) {
        // 获取视频源
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(rtsp);
        grabber.setOption("rtsp_transport","tcp");
        //设置帧率
        grabber.setFrameRate(FRAME_RATE);
        //设置获取的视频宽度
        grabber.setImageWidth(FRAME_WIDTH);
        //设置获取的视频高度
        grabber.setImageHeight(FRAME_HEIGHT);
        //设置视频bit率
        grabber.setVideoBitrate(2000000);
        return grabber;
    }

    /**
     * 获取到OnvifDevice对象
     * @param host 摄像头地址 92.168.xx.yy, or http://host[:port]
     * @param username 用户名
     * @param password 密码
     * @param profileToken "MediaProfile000"  If empty, will use first profile.
     * @return
     */
    public static OnvifDevice getOnvifCredentials(String host, String username, String password, String profileToken){
        try {
            OnvifCredentials credentials = new OnvifCredentials(host, username, password, profileToken);
            //补全host
            URL u = credentials.getHost().startsWith("http")
                    ? new URL(credentials.getHost())
                    : new URL("http://" + credentials.getHost());
            return new OnvifDevice(u, credentials.getUser(), credentials.getPassword());
        } catch (MalformedURLException | ConnectException | SOAPException e) {
            e.printStackTrace();
            throw new RrException(e.getMessage());
        }
    }

    /**
     * 获取实时rtsp地址
     * @param onvifDevice 设备
     * @return
     * @throws Exception
     */
    public static String getRtspUrl(OnvifDevice onvifDevice) throws Exception {
        List<Profile> profiles = onvifDevice.getMedia().getProfiles();
        for (Profile profile : profiles) {
            String profileToken = profile.getToken();
            String rtsp = onvifDevice.getStreamUri(profileToken, TransportProtocol.RTSP);
            String uri = "rtsp://" + onvifDevice.getUser() + ":" + onvifDevice.getPassword() + "@" + rtsp.replace("rtsp://", "");
        }
        return "";
    }

    /**
     * 获取历史rtsp地址
     * @param onvifDevice 设备
     * @return
     */
    public static String getReplayUrl(OnvifDevice onvifDevice){
        List<GetRecordingsResponseItem> recordings = onvifDevice.getRecordingPort().getRecordings();
        for (GetRecordingsResponseItem recording : recordings) {
            String recordingToken = recording.getRecordingToken();
            String rtsp = onvifDevice.getReplayUri(onvifDevice, recordingToken, TransportProtocol.RTSP);
            String uri = "rtsp://" + onvifDevice.getUser() + ":" + onvifDevice.getPassword() + "@" + rtsp.replace("rtsp://", "");
        }
        return "";
    }

    /**
     * 转换 时间段为rtsp时间格式
     * @param start 开始
     * @param end 结束
     * @return ?starttime=20200908t093812z&endtime=20200908t104816z
     */
    public static String getRtspTimeSpace(LocalDateTime start, LocalDateTime end){
        Long st = start.toInstant(ZoneOffset.of("+8")).toEpochMilli();
        Long ed = end.toInstant(ZoneOffset.of("+8")).toEpochMilli();
        String ios8601St = getDate(st);
        String ios8601Ed = getDate(st);
        return "?starttime=" + ios8601St + "&endtime=" + ios8601Ed;
    }

    /**
     * 时间戳转换成IOS8601格式
     * @param beginTime
     * @return
     */
    public static String getDate(Long beginTime) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String bt = format.format(beginTime);
        Date date = null;
        try {
            date = format.parse(bt);
        } catch (Exception e) {
            e.printStackTrace();
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss'z'");
        return sdf.format(date);
    }

}
