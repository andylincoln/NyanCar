package com.example.sample;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by mmarkman on 12/6/14.
 */
public class GaugeView extends SurfaceView
        implements SurfaceHolder.Callback {
    private SurfaceHolder sh;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);



    Context ctx;

    BubbleThread thread;

    public GaugeView(Context context) {
        super(context);
        sh = getHolder();
        sh.addCallback(this);
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.FILL);

        ctx = context;
        setFocusable(true); // make sure we get key events
    }


    public BubbleThread getThread() {
        return thread;
    }
    public void surfaceCreated(SurfaceHolder holder) {
        thread = new BubbleThread(sh, ctx, new Handler());
        thread.setRunning(true);
        thread.start();
    }
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) 	  {
        thread.setSurfaceSize(width, height);
    }
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        thread.setRunning(false);
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }
    }










    class BubbleThread extends Thread {
        private int canvasWidth = 200;
        private int canvasHeight = 400;
        private static final int SPEED = 2;
        private boolean run = false;

        private float bubbleX;
        private float bubbleY;
        private float headingX;
        private float headingY;

        public BubbleThread(SurfaceHolder surfaceHolder, Context context,
                            Handler handler) {
            sh = surfaceHolder;
            handler = handler;
            ctx = context;
        }
        public void doStart() {
            synchronized (sh) {
                // Start bubble in centre and create some random motion
                bubbleX = canvasWidth / 2;
                bubbleY = canvasHeight / 2;
                headingX = (float) (-1 + (Math.random() * 2));
                headingY = (float) (-1 + (Math.random() * 2));
            }
        }
        public void run() {
            while (run) {
                Canvas c = null;
                try {
                    c = sh.lockCanvas(null);
                    synchronized (sh) {

                        doDraw(c);
                    }
                    c.save();
                } finally {
                    if (c != null) {
                        sh.unlockCanvasAndPost(c);
                    }
                }
            }
        }

        public void setRunning(boolean b) {
            run = b;
        }
        public void setSurfaceSize(int width, int height) {
            synchronized (sh) {
                canvasWidth = width;
                canvasHeight = height;
                doStart();
            }
        }

        public void setY(int n){
            float max = 8000, x = 0;
            x = n/max;
            n = (int)((float)canvasHeight * x);
/*

            bubbleY = n;

            bubbleX = canvasWidth / 2;
            bubbleY = canvasHeight / 2;
*/
        }

        private void doDraw(Canvas canvas) {
            /*
            bubbleX = bubbleX + (headingX * SPEED);
            bubbleY = bubbleY + (headingY * SPEED);
            */
            canvas.restore();
            canvas.drawColor(Color.BLACK);
            canvas.drawCircle(bubbleX, bubbleY, 50, paint);

        }
    }
}