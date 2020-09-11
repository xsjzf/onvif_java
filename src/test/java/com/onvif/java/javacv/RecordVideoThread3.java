package com.onvif.java.javacv;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameGrabber.Exception;

import java.io.IOException;

import static org.bytedeco.ffmpeg.global.avcodec.av_free_packet;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;

/**
 * rtsp转 udp（转封装方式）
 * @author zf
 */
public class RecordVideoThread3 {
    FFmpegFrameGrabber grabber = null;
    FFmpegFrameRecorder record = null;
    int width = -1, height = -1;

    // 视频参数
    protected int audiocodecid;
    protected int codecid;
    protected double framerate;// 帧率
    protected int bitrate;// 比特率
    protected int GOP_LENGTH_IN_FRAMES = 20;// 比特率

    // 音频参数
    // 想要录制音频，这三个参数必须有：audioChannels > 0 && audioBitrate > 0 && sampleRate > 0
    private int audioChannels;
    private int audioBitrate;
    private int sampleRate;

    /**
     * 选择视频源
     * @param src
     * @author eguid
     * @throws Exception
     */
    public RecordVideoThread3 from(String src) throws Exception {
        // 采集/抓取器
        grabber = new FFmpegFrameGrabber(src);
        if(src.contains("rtsp")) {
            grabber.setOption("rtsp_transport","tcp");
        }
        grabber.start();// 开始之后ffmpeg会采集视频信息，之后就可以获取音视频信息
        if (width < 0 || height < 0) {
            width = grabber.getImageWidth();
            height = grabber.getImageHeight();
        }
        // 视频参数
        audiocodecid = grabber.getAudioCodec();
        System.err.println("音频编码：" + audiocodecid);
        codecid = grabber.getVideoCodec();
        framerate = grabber.getVideoFrameRate();// 帧率
        bitrate = grabber.getVideoBitrate();// 比特率
        // 音频参数
        // 想要录制音频，这三个参数必须有：audioChannels > 0 && audioBitrate > 0 && sampleRate > 0
        audioChannels = grabber.getAudioChannels();
        audioBitrate = grabber.getAudioBitrate();
        if (audioBitrate < 1) {
            audioBitrate = 128 * 1000;// 默认音频比特率
        }
        return this;
    }

    /**
     * 选择输出
     * @param out
     * @author eguid
     * @throws IOException
     */
    public RecordVideoThread3 to(String out) throws IOException {
        // 录制/推流器
        record = new FFmpegFrameRecorder(out, width, height);
        // 降低编码延时
        record.setVideoOption("tune", "zerolatency");
        // 提升编码速度
        record.setVideoOption("preset", "ultrafast");
        // 不可变(固定)音频比特率
        record.setVideoOption("crf", "23");
        // 音频最高质量
//        record.setAudioQuality(0);
        //gop间隔
        record.setGopSize(GOP_LENGTH_IN_FRAMES);
        //帧率
        record.setFrameRate(framerate);
        record.setVideoBitrate(bitrate);
        // 双通道(立体声)
        record.setAudioChannels(audioChannels);
        // 音频比特率
        record.setAudioBitrate(audioBitrate);
        // 音频采样率
        record.setSampleRate(sampleRate);
        // yuv420p
        record.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        record.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
        AVFormatContext fc;
        record.setFormat("h264");
        fc = grabber.getFormatContext();
        record.start(fc);
        return this;
    }
    
    /**
     * 转封装
     * @author eguid
     * @throws IOException
     */
    public RecordVideoThread3 go() throws IOException {
        System.out.println("开始推送...");
        long err_index = 0;//采集或推流导致的错误次数
        //连续五次没有采集到帧则认为视频采集结束，程序错误次数超过1次即中断程序
        for(int no_frame_index = 0; no_frame_index < 10 || err_index > 1;) {
            AVPacket pkt;
            try {
                //没有解码的音视频帧
                pkt = grabber.grabPacket();
                if(pkt == null || pkt.size() <= 0 || pkt.data() == null) {
                    //空包记录次数跳过
                    no_frame_index ++;
                    continue;
                }
                //不需要编码直接把音视频帧推出去
                err_index+=(record.recordPacket(pkt) ? 0 : 1);//如果失败err_index自增1
                av_packet_unref(pkt);
            } catch (IOException e) {//推流失败
                err_index++;
            }
        }
        return this;
    }

    public static void main(String[] args) throws Exception, IOException {

        //运行，设置视频源和推流地址
        new RecordVideoThread3().from("rtsp://admin:ghostprince678@192.168.1.3:1554/Streaming/Unicast/channels/1002")
        .to("udp://127.0.0.1:5001")
        .go();
    }
}