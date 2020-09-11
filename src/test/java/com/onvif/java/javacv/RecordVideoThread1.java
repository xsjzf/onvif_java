package com.onvif.java.javacv;

import com.onvif.java.utils.MediaUtils;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.IplImage;

/**
 * @program: onvif_java
 * @description:
 * @author: zf
 * @create: 2020-09-10 19:49
 **/
public class RecordVideoThread1 {
    /**
     * 按帧录制本机摄像头视频（边预览边录制，停止预览即停止录制）
     *
     * @author eguid
     * @param outputFile -录制的文件路径，也可以是rtsp或者rtmp等流媒体服务器发布地址
     * @param frameRate - 视频帧率
     * @throws Exception
     * @throws InterruptedException
     * @throws org.bytedeco.javacv.FrameRecorder.Exception
     */
    public static void recordCamera(String outputFile, String rtsp,  double frameRate){
        try {
            //另一种方式获取摄像头，opencv抓取器方式获取摄像头请参考第一章，FrameGrabber会自己去找可以打开的摄像头的抓取器。
            FrameGrabber grabber = MediaUtils.createGrabber(rtsp);//本机摄像头默认0
            grabber.start();//开启抓取器

            OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();//转换器
            IplImage grabbedImage = converter.convert(grabber.grab());//抓取一帧视频并将其转换为图像，至于用这个图像用来做什么？加水印，人脸识别等等自行添加
            int width = grabbedImage.width();
            int height = grabbedImage.height();

            FrameRecorder recorder = FrameRecorder.createDefault(outputFile, width, height);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264); // avcodec.AV_CODEC_ID_H264，编码
            recorder.setFormat("h264");//封装格式，如果是推送到rtmp就必须是flv封装格式
            recorder.setFrameRate(frameRate);

            recorder.start();//开启录制器
            long startTime=0;
            long videoTS=0;
//            CanvasFrame frame = new CanvasFrame("camera", CanvasFrame.getDefaultGamma() / grabber.getGamma());
//            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//            frame.setAlwaysOnTop(true);
            Frame rotatedFrame=converter.convert(grabbedImage);//不知道为什么这里不做转换就不能推到rtmp
            while ((grabbedImage = converter.convert(grabber.grab())) != null) {

                rotatedFrame = converter.convert(grabbedImage);
                if (startTime == 0) {
                    startTime = System.currentTimeMillis();
                }
                videoTS = 1000 * (System.currentTimeMillis() - startTime);
                recorder.setTimestamp(videoTS);
                recorder.record(rotatedFrame);
            }
            recorder.close();//关闭推流录制器，close包含release和stop操作
            grabber.close();//关闭抓取器
        } catch (FrameGrabber.Exception e) {
            e.printStackTrace();
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        recordCamera("udp://127.0.0.1:5001", "rtsp://admin:ghostprince678@192.168.1.3:1554/Streaming/Unicast/channels/1002", 2000000);
    }
}
