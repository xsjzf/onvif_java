package com.onvif.java.onvif;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;

/** @author Brad Lowe */
public class ReplayDevice {
  private static final Logger LOG = LoggerFactory.getLogger(ReplayDevice.class);


  public static void main(String[] args) {

  }
  public void frameRecord(String inputFile, OutputStream outputFile, int audioChannel)
          throws Exception, org.bytedeco.javacv.FrameRecorder.Exception {

    // 获取视频源
    FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile);
    grabber.setOption("rtsp_transport","tcp");
    grabber.setFrameRate(30);
    grabber.setVideoBitrate(3000000);
    // 流媒体输出地址，分辨率（长，高），是否录制音频（0:不录制/1:录制）
    FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputFile, 1280, 720,audioChannel);
    recorder.setFrameRate(30);
    recorder.setVideoBitrate(3000000);
    recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264); //avcodec.AV_CODEC_ID_H264  //AV_CODEC_ID_MPEG4
    recordByFrame(grabber, recorder);
  }

  private void recordByFrame(FFmpegFrameGrabber grabber, FFmpegFrameRecorder recorder)
          throws Exception, org.bytedeco.javacv.FrameRecorder.Exception {
    try {// 建议在线程中使用该方法

      grabber.start();
      recorder.start();
      //CanvasFrame canvas = new CanvasFrame("摄像头");//新建一个窗口
      //    canvas.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      //canvas.setAlwaysOnTop(true);

      long t1 = System.currentTimeMillis();

      Frame frame = null;


      while (true && (frame = grabber.grabFrame()) != null) {
        long t2 = System.currentTimeMillis();
        if(t2-t1 > 2*60*60*1000){
          break;
        }else{
          recorder.record(frame);
          //TODO your work
        }
        //canvas.showImage(grabber.grab());//获取摄像头图像并放到窗口上显示， 这里的Frame frame=grabber.grab(); frame是一帧视频图像
      }
      recorder.stop();
      grabber.stop();

    } finally {
      if (grabber != null) {
        grabber.stop();
      }
    }
  }

}
