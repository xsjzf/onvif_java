package com.onvif.java.javacv;

import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;

public class TestClass {
    public static void main(String[] args) throws Exception{
    	final int captureWidth = 1280;
        final int captureHeight = 720;
        final FFmpegFrameGrabber grabber = new FFmpegFrameGrabber("rtsp://admin:ghostprince678@192.168.1.3:1554/Streaming/Unicast/channels/1402");
        grabber.setImageWidth(captureWidth);
        grabber.setImageHeight(captureHeight);
        // rtsp格式一般添加TCP配置，否则丢帧会比较严重
        // Brick在测试过程发现，该参数改成udp可以解决部分电脑出现的下列报警，但是丢帧比较严重
        // av_interleaved_write_frame() error -22 while writing interleaved video packet.
        grabber.setOption("rtsp_transport", "tcp");
        grabber.start();
        // 最后一个参数是AudioChannels，建议通过grabber获取
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputStream, captureWidth, captureHeight, 1);
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
        recorder.setFormat("flv");
        // 视频帧率
        recorder.setFrameRate(25);
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
//            try {
//                System.out.println(outputStream.toByteArray().length);
//                start.getChannel().writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer(outputStream.toByteArray()),
//                        new InetSocketAddress("192.168.30.99",5555))).sync();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        }
        cFrame.dispose();
        recorder.close();
        grabber.close();
    }
}