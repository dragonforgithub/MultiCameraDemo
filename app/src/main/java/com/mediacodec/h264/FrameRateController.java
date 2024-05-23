package com.mediacodec.h264;

class FrameRateController {
    private long 	mStartTime = 0;
    private float 	mFPS= 0;
    private long 	mFrameCount = -1;

    public FrameRateController(float fps) {
        mFPS = fps;
    }

    public void Destroy() {

    }

    public boolean IsValidFrame(long current_time) {
        if( mFrameCount == -1)
        {
            mStartTime = current_time;
            mFrameCount = 0;
        }

        float elapsed = (current_time - mStartTime)/(float)1000.0;
        int cur_frame = (int)(mFPS * elapsed);
        if(cur_frame >= mFrameCount) {
            mFrameCount++;
            return true;
        }

        return false;
    }
}
