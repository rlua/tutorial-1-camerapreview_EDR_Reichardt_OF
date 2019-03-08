package org.opencv.samples.tutorial1;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;

import org.opencv.core.CvType;
import org.opencv.core.Core;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public class Tutorial1Activity extends Activity implements CvCameraViewListener2 {
    private static final String TAG = "OCVSample::Activity";

    private CameraBridgeViewBase mOpenCvCameraView;
    private boolean              mIsJavaCamera = true;
    private MenuItem             mItemSwitchCamera = null;

    //
    private Mat mEMA=null;
    //private Mat mOutput=null;
    //private Mat mOnes=null;
    private Mat mpreviousMat=null;
    private Mat mflowMat=null;
    private Mat mU=null,mV=null;
    private Mat minputMatFloat=null;
    private Mat mbuffMat=null;
    private Mat mbuffOffMat=null;
    private Mat mZeroMat=null;
    private Mat mShifted=null;
    private Mat mpreviousMatShifted=null;
    private Mat mHighPass=null;
    private Mat mLowPass=null;
    private double[] mEMAbuff=null;
    private double[] mbuff=null;
    private double[] mbuff_Off=null;
    private double mAlpha=0.5;
    private double mMuOn=0.05;
    private double mMuOff=-0.1;
    private double mAlphaHighPass=0.5;
    private double mAlphaLowPass=0.1;
    private int mChoice=3;


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public Tutorial1Activity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.tutorial1_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.tutorial1_activity_java_surface_view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);

        mOpenCvCameraView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                #mChoice=(mChoice+1)%4;
                mChoice=(mChoice+1)%5;
                return false;
            }
        });
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        //
        mEMA=new Mat(width,height,CvType.CV_32F);
        //mOnes = Mat.ones(width,height,CvType.CV_32F);
        //mOutput = new Mat(width,height,CvType.CV_32F);

        //This is for saving the input image data into convenient Float (double) instead of byte
        minputMatFloat=new Mat(width,height,CvType.CV_64F);

        int nchannels=1;
        mEMAbuff = new double[width*height*nchannels];
        mbuff = new double[width*height*nchannels];
        mbuff_Off = new double[width*height*nchannels];
        //Assume width*height*nchannels is the same as size = (int) inputMat.total() * inputMat.channels(); (below)
        for(int i=0;i<mEMAbuff.length;i++)
        {
            mEMAbuff[i]=0;
            mbuff[i]=0;
            mbuff_Off[i]=0;
        }

        //For Video.calcOpticalFlowFarneback
        //mpreviousMat=Mat.zeros(width,height,CvType.CV_8S);
        mflowMat=Mat.zeros(width,height,CvType.CV_32FC2); //Use CV_32FC2 according to others
        mU=new Mat();
        mV=new Mat();

    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        //Modify input camera image
        //Mat inputMat = inputFrame.rgba();

        Mat inputMat = inputFrame.gray();

        int choice=mChoice; //0 - EDR fast, 1 - Reichardt, 2 - OF-Farneback

        //Didn't work
        //Core.gemm(inputMat,mOnes,0.5,mEMA,0.5, mOutput);
        //Core.add((mEMA,inputMat,mEMA);

        if(choice==-1) {

            // EDR. Very Slow. Not Vectorized.

            //The moving average can also be done with Imgproc.accumulateWeighted(Mat src, Mat dst, double alpha)
            int size = (int) inputMat.total() * inputMat.channels();
            //double[] buff = new double[size];
            //inputMat.get(0, 0, mbuff);

            //http://answers.opencv.org/question/14961/using-get-and-put-to-access-pixel-values-in-java/
            int ibuff=0;
            for(int i=0;i<inputMat.size().height;i++)
            {
                for(int j=0;j<inputMat.size().width;j++)
                {
                    double[] data=inputMat.get(i,j); //I think calling these inside the double loop makes it very slow
                    //Calc EMA
                    mbuff[ibuff]=data[0];
                    mEMAbuff[ibuff]=(1-mAlpha)*mEMAbuff[ibuff]+mAlpha*mbuff[ibuff];
                    ibuff++;
                }
            }

            //for(int i=0; i<mEMAbuff.length; i++)
            //{
            //    mEMAbuff[i]=(1-mAlpha)*mEMAbuff[i]+mAlpha*mbuff[i];
            //}

            double minval=1e5,maxval=0;
            for(int i = 0; i < size; i++)
            {
                //buff[i] = (buff[i] > 0) ? 1 : (byte)0;
                //buff[i] = (byte)(-buff[i]); //photo-negative effect works
                //The 1e-5 follows from retina_convert.py
                mbuff[i] = Math.max(Math.tanh(Math.log(mbuff[i]/(mEMAbuff[i]+1e-5)))-mMuOn,0);
                if(mbuff[i]>maxval)
                {
                    maxval=mbuff[i];
                }
                if(mbuff[i]<minval)
                {
                    minval=mbuff[i];
                }
            }

            if(maxval>minval) {
                for(int i=0;i<size;i++)
                {
                    mbuff[i]=255.0 * (mbuff[i] - minval)/ (maxval - minval);
                }

                ibuff=0;
                for(int i=0;i<inputMat.size().height;i++)
                {
                    for(int j=0;j<inputMat.size().width;j++)
                    {
                        inputMat.put(i,j,mbuff[ibuff]);
                        ibuff++;
                    }
                }
                //Mat bv = new Mat(inputMat.size(), CvType.CV_8U);
                //bv.put(0, 0, mbuff);
                return inputMat;
            }

        }
        else if(choice==0) {

            //alpha=0.5
            // EDR. Faster.

            //The moving average can also be done with Imgproc.accumulateWeighted(Mat src, Mat dst, double alpha)

            int size = (int) inputMat.total() * inputMat.channels();

            if(mbuffOffMat==null)
            {
                mbuffOffMat=inputMat.clone();
                mZeroMat=Mat.zeros(inputMat.size(),inputMat.type());
            }

            //https://stackoverflow.com/questions/17035005/using-get-and-put-to-access-pixel-values-in-opencv-for-java
            inputMat.convertTo(minputMatFloat,CvType.CV_64F);
            //double[] buff = new double[size];
            minputMatFloat.get(0, 0, mbuff);

            for(int i=0; i<mEMAbuff.length; i++)
            {
                mEMAbuff[i]=(1-mAlpha)*mEMAbuff[i]+mAlpha*mbuff[i];
            }

            double minval=1e5,maxval=0;
            double minval_Off=1e5,maxval_Off=0;
            for(int i = 0; i < size; i++)
            {
                //buff[i] = (buff[i] > 0) ? 1 : (byte)0;
                //buff[i] = (byte)(-buff[i]); //photo-negative effect works
                //The 1e-5 follows from retina_convert.py
                double change=Math.tanh(Math.log(mbuff[i]/(mEMAbuff[i]+1e-5)));
                mbuff[i] = Math.max(change-mMuOn,0);
                mbuff_Off[i] = Math.max(mMuOff-change,0);

                if(mbuff[i]>maxval)
                {
                    maxval=mbuff[i];
                }
                if(mbuff[i]<minval)
                {
                    minval=mbuff[i];
                }

                if(mbuff_Off[i]>maxval_Off)
                {
                    maxval_Off=mbuff_Off[i];
                }
                if(mbuff_Off[i]<minval_Off)
                {
                    minval_Off=mbuff_Off[i];
                }
            }

            if(maxval>minval) {

                for(int i=0;i<size;i++)
                {
                    mbuff[i]=255.0 * (mbuff[i] - minval)/ (maxval - minval);
                    mbuff_Off[i]=255.0 * (mbuff_Off[i] - minval_Off)/ (maxval_Off - minval_Off);
                }

                inputMat.put(0, 0, mbuff); //reuse inputMat
                mbuffOffMat.put(0,0, mbuff_Off);

                //Merge planes
                List<Mat> mv=new ArrayList<>();
                mv.add(mbuffOffMat); //R - off
                mv.add(inputMat); //G - on
                mv.add(mZeroMat); //B

                Core.merge(mv,inputMat); //reuse inputMat again

                return inputMat;
            }

        }

        else if(choice==1) {

            //alpha=0.9
            // EDR. Faster.

            //The moving average can also be done with Imgproc.accumulateWeighted(Mat src, Mat dst, double alpha)

            int size = (int) inputMat.total() * inputMat.channels();

            if(mbuffOffMat==null)
            {
                mbuffOffMat=inputMat.clone();
                mZeroMat=Mat.zeros(inputMat.size(),inputMat.type());
            }

            //https://stackoverflow.com/questions/17035005/using-get-and-put-to-access-pixel-values-in-opencv-for-java
            inputMat.convertTo(minputMatFloat,CvType.CV_64F);
            //double[] buff = new double[size];
            minputMatFloat.get(0, 0, mbuff);

            for(int i=0; i<mEMAbuff.length; i++)
            {
                mEMAbuff[i]=(1-0.9)*mEMAbuff[i]+0.9*mbuff[i];
            }

            double minval=1e5,maxval=0;
            double minval_Off=1e5,maxval_Off=0;
            for(int i = 0; i < size; i++)
            {
                //buff[i] = (buff[i] > 0) ? 1 : (byte)0;
                //buff[i] = (byte)(-buff[i]); //photo-negative effect works
                //The 1e-5 follows from retina_convert.py
                double change=Math.tanh(Math.log(mbuff[i]/(mEMAbuff[i]+1e-5)));
                mbuff[i] = Math.max(change-mMuOn,0);
                mbuff_Off[i] = Math.max(mMuOff-change,0);

                if(mbuff[i]>maxval)
                {
                    maxval=mbuff[i];
                }
                if(mbuff[i]<minval)
                {
                    minval=mbuff[i];
                }

                if(mbuff_Off[i]>maxval_Off)
                {
                    maxval_Off=mbuff_Off[i];
                }
                if(mbuff_Off[i]<minval_Off)
                {
                    minval_Off=mbuff_Off[i];
                }
            }

            if(maxval>minval) {

                for(int i=0;i<size;i++)
                {
                    mbuff[i]=255.0 * (mbuff[i] - minval)/ (maxval - minval);
                    mbuff_Off[i]=255.0 * (mbuff_Off[i] - minval_Off)/ (maxval_Off - minval_Off);
                }

                inputMat.put(0, 0, mbuff); //reuse inputMat
                mbuffOffMat.put(0,0, mbuff_Off);

                //Merge planes
                List<Mat> mv=new ArrayList<>();
                mv.add(mbuffOffMat); //R - off
                mv.add(inputMat); //G - on
                mv.add(mZeroMat); //B

                Core.merge(mv,inputMat); //reuse inputMat again

                return inputMat;
            }

        }


        else if(choice==2)
        {
            //Reichardt detector, one direction

            if(mHighPass==null || mLowPass==null) {
                mHighPass=Mat.zeros(inputMat.size(),CvType.CV_64F);
                mLowPass=Mat.zeros(inputMat.size(),CvType.CV_64F);
                mbuffMat=Mat.zeros(inputMat.size(),CvType.CV_64F);
            }

            inputMat.convertTo(mbuffMat,CvType.CV_64F);
            //Step 1: Calculate high pass and low pass using EMA. Low pass corresponds to the time-delayed version.
            Imgproc.accumulateWeighted(inputMat,mHighPass,mAlphaHighPass);
            Imgproc.accumulateWeighted(inputMat,mLowPass,mAlphaLowPass);
            Core.subtract(mbuffMat,mHighPass,mHighPass);

            int dx=1;
            //Mat.mul may be creating new Mat objects on every call.
            //Core.addWeighted(mLowPass.colRange(0,mLowPass.width()-dx).mul(mHighPass.colRange(dx,mHighPass.width())), 1,
            //        mHighPass.colRange(0,mHighPass.width()-dx).mul(mLowPass.colRange(dx,mLowPass.width())), -1, 0,
            //        mbuffMat.colRange(0,mbuffMat.width()-dx));

            //Tested below if better than above which calls Mat.mul. Looks like it lasts a little longer before app crashes.
            Mat tmp=mbuffMat.colRange(0,mbuffMat.width()-dx);
            Core.multiply(mLowPass.colRange(0,mLowPass.width()-dx),mHighPass.colRange(dx,mHighPass.width()),tmp);
            Mat tmp2=minputMatFloat.colRange(0,minputMatFloat.width()-dx);
            Core.multiply(mHighPass.colRange(0,mHighPass.width()-dx),mLowPass.colRange(dx,mLowPass.width()),tmp2);
            Core.addWeighted(tmp,1,tmp2,-1,0,tmp);

            //To test
            //mHighPass.convertTo(inputMat,inputMat.type());
            //mLowPass.convertTo(inputMat,inputMat.type());

            //mbuffMat.convertTo(inputMat,inputMat.type());

            Core.MinMaxLocResult mm=Core.minMaxLoc(mbuffMat);
            //This is approximate rescaling, because |minVal| might be bigger than maxVal.
            Core.convertScaleAbs(mbuffMat,inputMat,255.0/mm.maxVal,0);

            //System.gc();
            return inputMat;
        }
        else if(choice==3)
        {
            //Optical Flow, OF-Farneback test.
            //Even Farneback is very slow.

            if(mpreviousMat!=null)
            {
                //Parameter values taken from /Users/rcl/Workspace/Neuroscience/Retina/OpticalFlow/test/dense_flow-opencv-3.1/src/dense_flow.cpp
                Video.calcOpticalFlowFarneback(mpreviousMat, inputMat, mflowMat, 0.702, 5, 10, 2, 7, 1.5, Video.OPTFLOW_FARNEBACK_GAUSSIAN);
            }
            mpreviousMat=inputMat.clone();
            //Mat U = new Mat();
            //Mat V = new Mat();
            Core.extractChannel(mflowMat, mU, 0);
            //Core.extractChannel(mflowMat, mV, 1);
            Core.MinMaxLocResult mm=Core.minMaxLoc(mU);
            if(mm.maxVal>mm.minVal) {
                //https://stackoverflow.com/questions/14539498/change-type-of-mat-object-from-cv-32f-to-cv-8u/14539652
                mU.convertTo(mV, CvType.CV_8U, 255.0 / (mm.maxVal - mm.minVal), -255.0 * mm.minVal / (mm.maxVal - mm.minVal));
                return mV;
            }
        }
        else if(choice==4)
        {
            //TODO Gradient detector (Borst and Euler, 2011)
            return inputMat;
        }

        return inputMat;

    }


}
