package net.njnu.locationbasedstreaming;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PreviewCallback;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;

import java.io.IOException;
import java.nio.ShortBuffer;

import net.njnu.camera.R;

import com.googlecode.javacv.FFmpegFrameRecorder;
import com.googlecode.javacv.cpp.opencv_core.IplImage;

import static com.googlecode.javacv.cpp.opencv_core.*;

public class LiveStreamActivity extends Activity implements OnClickListener {
	
    private final static String LOG_TAG = "MainActivity";

    private PowerManager.WakeLock mWakeLock;

    //private String ffmpeg_link = "rtmp://103.10.24.209/cam1/screen camera";
	private String ffmpeg_link = "rtmp://222.192.6.56/oflaDemo/";
	private String stream_name = "stream1512268875505";
    //private String ffmpeg_link = "/mnt/sdcard/new_stream.flv";
	
    private volatile FFmpegFrameRecorder recorder;
    boolean recording = false;
    long startTime = 0;

    private int sampleAudioRateInHz = 44100;
    private int imageWidth = 480;
    private int imageHeight = 320;
    private int frameRate = 30;

    private Thread audioThread;
    volatile boolean runAudioThread = true;
    private AudioRecord audioRecord;
    private AudioRecordRunnable audioRecordRunnable;

    private CameraView cameraView;
    private IplImage yuvIplimage = null;
    
    private Button recordButton;
    private LinearLayout mainLayout;
    
    private Camera camera;//�������
    private int cameraPosition = 1;//0����ǰ������ͷ��1�����������ͷ
    private SurfaceHolder holder;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        ffmpeg_link = ffmpeg_link + stream_name;
      
        setContentView(R.layout.live_stream);

        initLayout();
        initRecorder();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE); 
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, LOG_TAG); 
            mWakeLock.acquire(); 
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        recording = false;
    }


    private void initLayout() {

        mainLayout = (LinearLayout) this.findViewById(R.id.record_layout);

        recordButton = (Button) findViewById(R.id.recorder_control);
        recordButton.setText("Start");
        recordButton.setOnClickListener(this);
        findViewById(R.id.camera_position).setOnClickListener(this);
        
        cameraView = new CameraView(this);
        
        LinearLayout.LayoutParams layoutParam = new LinearLayout.LayoutParams(imageWidth, imageHeight);        
        mainLayout.addView(cameraView, layoutParam);
        Log.v(LOG_TAG, "added cameraView to mainLayout");
    }

    private void initRecorder() {
        Log.w(LOG_TAG,"initRecorder");

        if (yuvIplimage == null) {
        	// Recreated after frame size is set in surface change method
            yuvIplimage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_8U, 2);
        	//yuvIplimage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_32S, 2);

            Log.v(LOG_TAG, "IplImage.create");
        }

        recorder = new FFmpegFrameRecorder(ffmpeg_link, imageWidth, imageHeight, 1);
        Log.v(LOG_TAG, "FFmpegFrameRecorder: " + ffmpeg_link + " imageWidth: " + imageWidth + " imageHeight " + imageHeight);

        recorder.setFormat("flv");
        Log.v(LOG_TAG, "recorder.setFormat(\"flv\")");
        
        recorder.setSampleRate(sampleAudioRateInHz);
        Log.v(LOG_TAG, "recorder.setSampleRate(sampleAudioRateInHz)");

        // re-set in the surface changed method as well
        recorder.setFrameRate(frameRate);
        Log.v(LOG_TAG, "recorder.setFrameRate(frameRate)");

        // Create audio recording thread
        audioRecordRunnable = new AudioRecordRunnable();
        audioThread = new Thread(audioRecordRunnable);
    }

    // Start the capture
    public void startRecording() {
        try {
            recorder.start();
            startTime = System.currentTimeMillis();
            recording = true;
            audioThread.start();
        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecording() {
    	// This should stop the audio thread from running
    	runAudioThread = false;

        if (recorder != null && recording) {
            recording = false;
            Log.v(LOG_TAG,"Finishing recording, calling stop and release on recorder");
            try {
                recorder.stop();
                recorder.release();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            recorder = null;
        }
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	// Quit when back button is pushed
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (recording) {
                stopRecording();
            }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @SuppressLint("NewApi") @Override
    public void onClick(View v) {
       
    if(v.getId()== R.id.camera_position){
        //�л�ǰ������ͷ
        int cameraCount = 0;
        CameraInfo cameraInfo = new CameraInfo();
        cameraCount = Camera.getNumberOfCameras();//�õ�����ͷ�ĸ���

        for(int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);//�õ�ÿһ������ͷ����Ϣ
            if(cameraPosition == 1) {
                //�����Ǻ��ã����Ϊǰ��
                if(cameraInfo.facing  == Camera.CameraInfo.CAMERA_FACING_FRONT) {//��������ͷ�ķ�λ��CAMERA_FACING_FRONTǰ��      CAMERA_FACING_BACK����  
                    camera.stopPreview();//ͣ��ԭ������ͷ��Ԥ��
                    camera.release();//�ͷ���Դ
                    camera = null;//ȡ��ԭ������ͷ
                    camera = Camera.open(i);//�򿪵�ǰѡ�е�����ͷ
                    try {
                        camera.setPreviewDisplay(holder);//ͨ��surfaceview��ʾȡ������
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    camera.startPreview();//��ʼԤ��
                    cameraPosition = 0;
                    break;
                }
            } else {
                //������ǰ�ã� ���Ϊ����
                if(cameraInfo.facing  == Camera.CameraInfo.CAMERA_FACING_BACK) {//��������ͷ�ķ�λ��CAMERA_FACING_FRONTǰ��      CAMERA_FACING_BACK����  
                    camera.stopPreview();//ͣ��ԭ������ͷ��Ԥ��
                    camera.release();//�ͷ���Դ
                    camera = null;//ȡ��ԭ������ͷ
                    camera = Camera.open(i);//�򿪵�ǰѡ�е�����ͷ
                    try {
                        camera.setPreviewDisplay(holder);//ͨ��surfaceview��ʾȡ������
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    camera.startPreview();//��ʼԤ��
                    cameraPosition = 1;
                    break;
                }
            	}

        	}
 
       }else{
    	   if (!recording) {
               startRecording();
               Log.w(LOG_TAG, "Start Button Pushed");
               recordButton.setText("Stop");
           } else {
               stopRecording();
               Log.w(LOG_TAG, "Stop Button Pushed");
               recordButton.setText("Start");
           }
       }
    }
    
    //---------------------------------------------
    // audio thread, gets and encodes audio data
    //---------------------------------------------
    class AudioRecordRunnable implements Runnable {

        @Override
        public void run() {
        	// Set the thread priority
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            // Audio
            int bufferSize;
            short[] audioData;
            int bufferReadResult;

            bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz, 
                    AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz, 
                    AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            audioData = new short[bufferSize];

            Log.d(LOG_TAG, "audioRecord.startRecording()");
            audioRecord.startRecording();

            // Audio Capture/Encoding Loop
            while (runAudioThread) {
            	// Read from audioRecord
                bufferReadResult = audioRecord.read(audioData, 0, audioData.length);
                if (bufferReadResult > 0) {
                    //Log.v(LOG_TAG,"audioRecord bufferReadResult: " + bufferReadResult);
                	
                    // Changes in this variable may not be picked up despite it being "volatile"
                    if (recording) {
                        try {
                        	// Write to FFmpegFrameRecorder
                            recorder.record(ShortBuffer.wrap(audioData, 0, bufferReadResult));
                        } catch (FFmpegFrameRecorder.Exception e) {
                            Log.v(LOG_TAG,e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
            Log.v(LOG_TAG,"AudioThread Finished");

            /* Capture/Encoding finished, release recorder */
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                Log.v(LOG_TAG,"audioRecord released");
            }
        }
    }

    class CameraView extends SurfaceView implements SurfaceHolder.Callback, PreviewCallback {

    	private boolean previewRunning = false;
    	
//        public SurfaceHolder holder;
//        public Camera camera;
        
        private byte[] previewBuffer;
        
        long videoTimestamp = 0;

        Bitmap bitmap;
        Canvas canvas;
        
        public CameraView(Context _context) {
            super(_context);
            
            holder = this.getHolder();
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
         	
			camera = Camera.open();
			try {
				camera.setPreviewDisplay(holder);
				camera.setPreviewCallback(this);
				
	            Camera.Parameters currentParams = camera.getParameters();
	            Log.v(LOG_TAG,"Preview Framerate: " + currentParams.getPreviewFrameRate());
	        	Log.v(LOG_TAG,"Preview imageWidth: " + currentParams.getPreviewSize().width + " imageHeight: " + currentParams.getPreviewSize().height);
	        	
	        	// Use these values
	        	imageWidth = currentParams.getPreviewSize().width;
	        	imageHeight = currentParams.getPreviewSize().height;
	        	frameRate = currentParams.getPreviewFrameRate();				
				
	        	bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ALPHA_8);
	    		
	        	
	        	/*
				Log.v(LOG_TAG,"Creating previewBuffer size: " + imageWidth * imageHeight * ImageFormat.getBitsPerPixel(currentParams.getPreviewFormat())/8);
	        	previewBuffer = new byte[imageWidth * imageHeight * ImageFormat.getBitsPerPixel(currentParams.getPreviewFormat())/8];
				camera.addCallbackBuffer(previewBuffer);
	            camera.setPreviewCallbackWithBuffer(this);
	        	*/				
				
				camera.startPreview();
				previewRunning = true;
			}
			catch (IOException e) {
				Log.v(LOG_TAG,e.getMessage());
				e.printStackTrace();
			}	
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.v(LOG_TAG,"Surface Changed: width " + width + " height: " + height);

            // We would do this if we want to reset the camera parameters
            /*
            if (!recording) {
    			if (previewRunning){
    				camera.stopPreview();
    			}

    			try {
    				//Camera.Parameters cameraParameters = camera.getParameters();
    				//p.setPreviewSize(imageWidth, imageHeight);
    			    //p.setPreviewFrameRate(frameRate);
    				//camera.setParameters(cameraParameters);
    				
    				camera.setPreviewDisplay(holder);
    				camera.startPreview();
    				previewRunning = true;
    			}
    			catch (IOException e) {
    				Log.e(LOG_TAG,e.getMessage());
    				e.printStackTrace();
    			}	
    		}            
            */
            
            // Get the current parameters
            Camera.Parameters currentParams = camera.getParameters();
            Log.v(LOG_TAG,"Preview Framerate: " + currentParams.getPreviewFrameRate());
        	Log.v(LOG_TAG,"Preview imageWidth: " + currentParams.getPreviewSize().width + " imageHeight: " + currentParams.getPreviewSize().height);
        	
        	// Use these values
        	imageWidth = currentParams.getPreviewSize().width;
        	imageHeight = currentParams.getPreviewSize().height;
        	frameRate = currentParams.getPreviewFrameRate();
        	
        	// Create the yuvIplimage if needed
			yuvIplimage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_8U, 2);
        	//yuvIplimage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_32S, 2);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                camera.setPreviewCallback(null);
                
    			previewRunning = false;
    			camera.release();
                
            } catch (RuntimeException e) {
            	Log.v(LOG_TAG,e.getMessage());
            	e.printStackTrace();
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            
            if (yuvIplimage != null && recording) {
            	videoTimestamp = 1000 * (System.currentTimeMillis() - startTime);

            	// Put the camera preview frame right into the yuvIplimage object
            	yuvIplimage.getByteBuffer().put(data);
                

                //Log.v(LOG_TAG,"Writing Frame");
                
                try {
                	
                	// Get the correct time
                    recorder.setTimestamp(videoTimestamp);
                    
                    // Record the image into FFmpegFrameRecorder
                    recorder.record(yuvIplimage);
                    
                } catch (FFmpegFrameRecorder.Exception e) {
                    Log.v(LOG_TAG,e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}