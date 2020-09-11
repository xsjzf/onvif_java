package com.onvif.java.javacv;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
 	import java.nio.ShortBuffer;
 	import java.util.concurrent.ScheduledThreadPoolExecutor;
 	import java.util.concurrent.TimeUnit;
 	 
 	import javax.sound.sampled.AudioFormat;
 	import javax.sound.sampled.AudioSystem;
 	import javax.sound.sampled.DataLine;
 	import javax.sound.sampled.LineUnavailableException;
 	import javax.sound.sampled.Mixer;
 	import javax.sound.sampled.TargetDataLine;

import com.onvif.java.utils.MediaUtils;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.FrameRecorder.Exception;

public class WebcamAndMicrophoneCapture
 	{
 	final private static int WEBCAM_DEVICE_INDEX= 1;
 	final private static int AUDIO_DEVICE_INDEX= 4;
 	 
 	final private static int FRAME_RATE = 30;
 	final private static int GOP_LENGTH_IN_FRAMES= 60;
 	 
 	private static long startTime= 0;
 	private static long videoTS= 0;
 	 
 	public static void main(String[] args) throws Exception, org.bytedeco.javacv.FrameGrabber.Exception
 	{
 	int captureWidth = 1280;
 	int captureHeight = 720;
 	 
 	// The available FrameGrabber classes include OpenCVFrameGrabber (opencv_videoio),
 	// DC1394FrameGrabber, FlyCaptureFrameGrabber, OpenKinectFrameGrabber,
 	// PS3EyeFrameGrabber, VideoInputFrameGrabber, and FFmpegFrameGrabber.
	FFmpegFrameGrabber grabber = MediaUtils.createGrabber("rtsp://admin:ghostprince678@192.168.1.3:1554/Streaming/Unicast/channels/1002");
	grabber.setImageWidth(captureWidth);
 	grabber.setImageHeight(captureHeight);
 	grabber.start();
 	 
 	// org.bytedeco.javacv.FFmpegFrameRecorder.FFmpegFrameRecorder(String
 	// filename, int imageWidth, int imageHeight, int audioChannels)
 	// For each param, we're passing in...
 	// filename = either a path to a local file we wish to create, or an
 	// RTMP url to an FMS / Wowza server
 	// imageWidth = width we specified for the grabber
 	// imageHeight = height we specified for the grabber
 	// audioChannels = 2, because we like stereo
 	FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(
 	"udp://127.0.0.1:5001",
 	captureWidth, captureHeight, 2);
 	recorder.setInterleaved(true);
 	 
 	// 减少FFMPEG中的启动延迟 (see:
 	// https://trac.ffmpeg.org/wiki/StreamingGuide)
 	recorder.setVideoOption("tune","zerolatency");
 	//在质量和编码速度之间进行权衡
	// 可能的值是超快，超快，非常快，更快，快，
	// 中，慢，慢，非常慢
	// 超快为我们提供最少的压缩量（更低的编码器// CPU）另一端需要较大的流大小
	// 非常慢的速度提供了最佳的压缩（高//编码器CPU），同时减小了流大小
 	// (see: https://trac.ffmpeg.org/wiki/Encode/H.264)
 	recorder.setVideoOption("preset","ultrafast");
 	// Constant Rate Factor (see: https://trac.ffmpeg.org/wiki/Encode/H.264)
 	recorder.setVideoOption("crf","28");
 	// 2000 kb/s, reasonable "sane" area for 720
 	recorder.setVideoBitrate(2000000);
 	recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
 	recorder.setFormat("flv");
 	// FPS (frames per second)
 	recorder.setFrameRate(FRAME_RATE);
 	// Key frame interval, in our case every 2 seconds -> 30 (fps) * 2 = 60
 	// (gop length)
 	recorder.setGopSize(GOP_LENGTH_IN_FRAMES);
 	 
 	// We don't want variable bitrate audio
 	recorder.setAudioOption("crf","0");
 	// Highest quality
 	recorder.setAudioQuality(0);
 	// 192 Kbps
 	recorder.setAudioBitrate(192000);
 	recorder.setSampleRate(44100);
 	recorder.setAudioChannels(2);
 	recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
 	 
 	// Jack 'n coke... do it...
 	recorder.start();
 	 
 	// Thread for audio capture, this could be in a nested private class if you prefer...
 	new Thread(new Runnable() {
 	@Override
 	public void run()
 	{
 	// Pick a format...
 	// NOTE: It is better to enumerate the formats that the system supports,
 	// because getLine() can error out with any particular format...
 	// For us: 44.1 sample rate, 16 bits, stereo, signed, little endian
 	AudioFormat audioFormat = new AudioFormat(44100.0F,16, 2, true, false);
 	 
 	// Get TargetDataLine with that format
 	Mixer.Info[] minfoSet= AudioSystem.getMixerInfo();
 	Mixer mixer = AudioSystem.getMixer(minfoSet[AUDIO_DEVICE_INDEX]);
 	DataLine.Info dataLineInfo= new DataLine.Info(TargetDataLine.class, audioFormat);
 	 
 	try
 	{
 	// Open and start capturing audio
 	// It's possible to have more control over the chosen audio device with this line:
 	// TargetDataLine line = (TargetDataLine)mixer.getLine(dataLineInfo);
 	TargetDataLine line = (TargetDataLine)AudioSystem.getLine(dataLineInfo);
 	line.open(audioFormat);
 	line.start();
 	 
 	int sampleRate = (int) audioFormat.getSampleRate();
 	int numChannels = audioFormat.getChannels();
 	 
 	// Let's initialize our audio buffer...
 	int audioBufferSize = sampleRate * numChannels;
 	byte[] audioBytes = new byte[audioBufferSize];
 	 
 	// Using a ScheduledThreadPoolExecutor vs a while loop with
 	// a Thread.sleep will allow
 	// us to get around some OS specific timing issues, and keep
 	// to a more precise
 	// clock as the fixed rate accounts for garbage collection
 	// time, etc
 	// a similar approach could be used for the webcam capture
 	// as well, if you wish
 	ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
 	exec.scheduleAtFixedRate(new Runnable() {
 	@Override
 	public void run()
 	{
 	try
 	{
 	// Read from the line... non-blocking
 	int nBytesRead = line.read(audioBytes,0, line.available());
 	 
 	// Since we specified 16 bits in the AudioFormat,
 	// we need to convert our read byte[] to short[]
 	// (see source from FFmpegFrameRecorder.recordSamples for AV_SAMPLE_FMT_S16)
 	// Let's initialize our short[] array
 	int nSamplesRead = nBytesRead / 2;
 	short[] samples = new short[nSamplesRead];
 	 
 	// Let's wrap our short[] into a ShortBuffer and
 	// pass it to recordSamples
 	ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
 	ShortBuffer sBuff = ShortBuffer.wrap(samples,0, nSamplesRead);
 	 
 	// recorder is instance of
 	// org.bytedeco.javacv.FFmpegFrameRecorder
 	recorder.recordSamples(sampleRate, numChannels, sBuff);
 	}
 	catch (org.bytedeco.javacv.FrameRecorder.Exception e)
 	{
 	e.printStackTrace();
 	}
 	}
 	}, 0, (long)1000 / FRAME_RATE, TimeUnit.MILLISECONDS);
 	}
 	catch (LineUnavailableException e1)
 	{
 	e1.printStackTrace();
 	}
 	}
 	}).start();
 	 
 	// A really nice hardware accelerated component for our preview...
// 	CanvasFrame cFrame = new CanvasFrame("Capture Preview",CanvasFrame.getDefaultGamma()/ grabber.getGamma());
 	 
 	Frame capturedFrame = null;
 	 
 	// While we are capturing...
 	while ((capturedFrame = grabber.grab()) != null)
 	{
// 	if (cFrame.isVisible())
// 	{
// 	// Show our frame in the preview
// 	cFrame.showImage(capturedFrame);
// 	}
 	 
 	// Let's define our start time...
 	// This needs to be initialized as close to when we'll use it as
 	// possible,
 	// as the delta from assignment to computed time could be too high
 	if (startTime == 0)
 	startTime = System.currentTimeMillis();
 	 
 	// Create timestamp for this frame
 	videoTS = 1000 * (System.currentTimeMillis()- startTime);
 	 
 	// Check for AV drift
 	if (videoTS > recorder.getTimestamp())
 	{
 	System.out.println(
 	"Lip-flap correction:"
 	+ videoTS + " :"
 	+ recorder.getTimestamp()+ " -> "
 	+ (videoTS - recorder.getTimestamp()));
 	 
 	// We tell the recorder to write this frame at this timestamp
 	recorder.setTimestamp(videoTS);
 	}
 	 
 	// Send the frame to the org.bytedeco.javacv.FFmpegFrameRecorder
 	recorder.record(capturedFrame);
 	}
 	 
// 	cFrame.dispose();
 	recorder.stop();
 	grabber.stop();
 	}
 	}