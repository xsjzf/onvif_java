package com.onvif.java.javacv;

import com.onvif.java.utils.MediaUtils;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.lang.reflect.Field;

public class RecordVideoThread4 {
    public static void main(String[] args) throws Exception{
    	final int captureWidth = MediaUtils.FRAME_WIDTH;
        final int captureHeight = MediaUtils.FRAME_HEIGHT;
        final FFmpegFrameGrabber grabber = new FFmpegFrameGrabber("rtsp://admin:ghostprince678@192.168.1.3:1554/Streaming/Unicast/channels/1002");
        grabber.setImageWidth(captureWidth);
        grabber.setImageHeight(captureHeight);
        // rtsp格式一般添加TCP配置，否则丢帧会比较严重
        // Brick在测试过程发现，该参数改成udp可以解决部分电脑出现的下列报警，但是丢帧比较严重
        // av_interleaved_write_frame() error -22 while writing interleaved video packet.
        grabber.setOption("rtsp_transport", "tcp");
        grabber.start();
        // 最后一个参数是AudioChannels，建议通过grabber获取
        final FFmpegFrameRecorder recorder = new FFmpegFrameRecorder("udp://127.0.0.1:5001",
                MediaUtils.FRAME_WIDTH, MediaUtils.FRAME_HEIGHT, 0);
        recorder.setInterleaved(true);
        // 降低编码延时
        recorder.setVideoOption("tune", "zerolatency");
        // 提升编码速度
        recorder.setVideoOption("preset", "ultrafast");
        // 视频质量参数(详见 https://trac.ffmpeg.org/wiki/Encode/H.264)
        recorder.setVideoOption("crf", "28");
        // 分辨率
        recorder.setVideoBitrate(2000000);
        // 视频编码格式
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        // 视频格式
        recorder.setFormat("h264");
        // 视频帧率
        recorder.setFrameRate(15);
        recorder.setGopSize(60);
        recorder.setAudioOption("crf", "0");
        recorder.setAudioQuality(0);
        recorder.setAudioBitrate(192000);
        recorder.setSampleRate(44100);
        // 建议从grabber获取AudioChannels
        recorder.setAudioChannels(grabber.getAudioChannels());
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
        recorder.start();
 
        // 解决音视频同步导致的延时问题
        Field field = recorder.getClass().getDeclaredField("oc");
        field.setAccessible(true);
        AVFormatContext oc = (AVFormatContext)field.get(recorder);
        oc.max_interleave_delta(100);
 
        // 用来测试的frame窗口
        final CanvasFrame cFrame = new CanvasFrame("frame");
        Frame capturedFrame = null;
 
        // 有些时候，程序执行回报下列错误，添加一行代码解决此问题
        // av_interleaved_write_frame() error -22 while writing interleaved video packet.
        grabber.flush();
        
        while ((capturedFrame = grabber.grab()) != null) {
        	if (cFrame.isVisible()){
        		cFrame.showImage(capturedFrame);
        	}
        	System.out.println(grabber.getFrameNumber()+ "--" +capturedFrame.timestamp);
        	recorder.setTimestamp(capturedFrame.timestamp);
        	recorder.record(capturedFrame);
        }
        cFrame.dispose();
        recorder.close();
        grabber.close();
    }
}