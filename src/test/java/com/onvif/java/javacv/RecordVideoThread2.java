package com.onvif.java.javacv;

import com.onvif.java.utils.MediaUtils;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;

public class RecordVideoThread2 extends Thread {
    // 流地址 网上有自行百度
    public String streamURL;
    // 文件路径
    public String filePath;

    public void setStreamURL(String streamURL) {
        this.streamURL = streamURL;
    }
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    @Override
    public void run() {
        System.out.println(streamURL);
        // 获取视频源
        FFmpegFrameGrabber grabber = MediaUtils.createGrabber(streamURL);
        FFmpegFrameRecorder recorder = null;
        try {
            grabber.start();
            Frame frame = grabber.grabFrame();
            if (frame != null) {
                // 流媒体输出地址，分辨率（长，高），是否录制音频（0:不录制/1:录制）
                recorder = new FFmpegFrameRecorder("udp://127.0.0.1:5001", MediaUtils.FRAME_WIDTH, MediaUtils.FRAME_HEIGHT, 0);
                // 直播流格式
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                // 降低编码延时
                recorder.setVideoOption("tune", "zerolatency");
                // 提升编码速度
                recorder.setVideoOption("preset", "ultrafast");
                // 录制的视频格式 flv(rtmp格式) h264(udp格式) mpegts(未压缩的udp) rawvideo
                recorder.setFormat("h264");
                // 帧数
                double frameLength = grabber.getLengthInFrames();
                long frameTime = grabber.getLengthInTime();
                double v = frameLength * 1000 * 1000 / frameTime;
                recorder.setFrameRate(v);
//                recorder.setAudioBitrate(192000);
                //百度翻译的比特率，默认400000，但是我400000贼模糊，调成800000比较合适
                recorder.setVideoBitrate(200000);
//                recorder.setGopSize(60);
                recorder.setAudioOption("crf", "23");
//                recorder.setAudioQuality(0);
//                recorder.setSampleRate(44100);
                // 建议从grabber获取AudioChannels
                recorder.setAudioChannels(grabber.getAudioChannels());
                recorder.setInterleaved(true);
                // yuv420p
                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
                recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                recorder.start(grabber.getFormatContext());
                AVPacket packet;
                long dts = 0;
                while (true) {
                    if ((packet = grabber.grabPacket()) != null){
                        long currentDts = packet.dts();
                        long pts = packet.pts();
                        if (currentDts >= dts) {
                            recorder.recordPacket(packet);
                        }
                        dts = currentDts;
                    }
                }
                // 停止录制
//                recorder.stop();
//                grabber.stop();
            }
        } catch (FrameGrabber.Exception | FrameRecorder.Exception e) {
            e.printStackTrace();
        } finally {
            try {
                grabber.stop();
            } catch (FrameGrabber.Exception e) {
                e.printStackTrace();
            }
            if (recorder != null) {
                try {
                    recorder.stop();
                } catch (FrameRecorder.Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    public static void main(String[] args) {
        RecordVideoThread2 thread = new RecordVideoThread2();
        thread.setFilePath("H:\\zf\\index\\ONVIF\\testOne.flv");
        thread.setStreamURL("rtsp://admin:ghostprince678@192.168.1.3:1554/Streaming/Unicast/channels/1002");
        thread.start();        
    }
}