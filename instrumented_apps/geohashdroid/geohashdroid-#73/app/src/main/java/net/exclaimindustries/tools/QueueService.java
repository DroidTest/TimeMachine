/*
 * QueueService.java
 * Copyright (C)2015 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.tools;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * <p>
 * A <code>QueueService</code> is similar in theory to an {@link android.app.IntentService},
 * with the exception that the <code>Intent</code> is stored in a queue and
 * dealt with that way.  This also means the queue can be observed and iterated
 * as need be to, for instance, get a list of currently-waiting things to
 * process.
 * </p>
 * 
 * <p>
 * Note that while <code>QueueService</code> has many superficial similarities
 * to <code>IntentService</code>, it is NOT a subclass of it.  They just don't
 * work similarly enough under the hood to justify it.
 * </p>
 * 
 * @author Nicholas Killewald
 */
public abstract class QueueService extends Service {
    private static final String DEBUG_TAG = "QueueService";

    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            // WE'RE IN A THREAD NOW!
            super(looper);
        }

        public void handleMessage(Message msg) {
            // Quick!  Hand this off to handleCommand!  It might start ANOTHER
            // thread to deal with this.
            handleCommand((Intent)msg.obj);
        }
    }
    
    /**
     * Codes returned from onHandleIntent that tells the queue what to do next.
     */
    protected enum ReturnCode {
        /** Queue should continue as normal. */
        CONTINUE,
        /**
         * Queue should pause until resumed later.  Useful for temporary
         * errors.  The queue will not be emptied, and the Intent which caused
         * this pause won't be removed (though see {@link #COMMAND_RESUME_SKIP_FIRST}).
         */
        PAUSE,
        /**
         * Queue should stop entirely and not be resumed.  This implies the
         * queue will be emptied.
         */
        STOP
    }
    
    /**
     * Internal prefix of serialized intent data.  Don't change this unless you
     * know you'll be running multiple QueueServices, which is the sole reason
     * it's not static or final.
     */
    protected String mInternalQueueFilePrefix = "Queue";
    
    /**
     * Send an Intent with this extra data in it, set to one of the command
     * statics, to send a command.
     */
    public static final String COMMAND_EXTRA = "net.exclaimindustries.tools.QUEUETHREAD_COMMAND";
    
    /**
     * Command code sent to ask a paused QueueService to resume processing.
     */
    public static final int COMMAND_RESUME = 0;
    /**
     * Command code sent to ask a paused QueueService to resume processing,
     * skipping the first thing in the queue.
     */
    public static final int COMMAND_RESUME_SKIP_FIRST = 1;
    /**
     * Command code sent to ask a paused QueueService to give up entirely and
     * empty the queue (and by extension stop the service).  Note that this is
     * NOT guaranteed to stop the queue if it is currently not paused.
     */
    public static final int COMMAND_ABORT = 2;
    
    private Queue<Intent> mQueue;
    private Thread mThread;
    
    // Whether or not the queue is currently paused.
    private volatile boolean mIsPaused;
    
    public QueueService() {
        super();
        
        // Give us a queue!
        mQueue = new ConcurrentLinkedQueue<>();
        
        // And we're not paused by default.
        mIsPaused = false;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // To recreate, we want to go through everything we have in storage in
        // the same order we wrote it out.
        String files[] = fileList();
        
        // But the only files we're interested in are Queue# files.
        int count = 0;
        
        for(String s : files) {
            if(s.startsWith(mInternalQueueFilePrefix))
                count++;
        }
        
        if(count >= 1) {
            // Now, open each one in order and have the deserializer deserialize
            // them.  And because we're being paranoid today, make sure we
            // account for gaps in the numbering.
            int processed = 0;
            
            int i = 0;
            
            while(processed < count) {
                try {
                    // All the queue files are named Queue#.  We know there are
                    // as many as the count variable.  We don't know if all
                    // those digits exist, though, so track how many files we
                    // deserialized and stop when we run out.  I really hope we
                    // don't wind up in an infinite loop here.
                    InputStream is = openFileInput(mInternalQueueFilePrefix + i);
                    
                    Intent intent = deserializeFromDisk(is);
                    if(intent != null) mQueue.add(intent);
                    
                    try {
                        is.close();
                    } catch (IOException e) {
                        // Ignore this.
                    }
                    
                    deleteFile(mInternalQueueFilePrefix + i);
                    processed++;
                } catch (FileNotFoundException e) {
                    // If we get here, we're apparently out of order.
                    Log.w(DEBUG_TAG, "Couldn't find " + mInternalQueueFilePrefix + i + ", apparently we missed a number when writing...");
                }
                
                i++;
            }
            
            // Always assume that a non-empty queue involved a pause somewhere.
            mIsPaused = true;
        }
        
        // Finally, restart the HandlerThread.  We'll wait for further
        // instructions.
        HandlerThread thread = new HandlerThread("QueueService Handler");
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public void onDestroy() {
        // Before destruction, serialize!  Make it snappy!
        int i = 0;
        
        if(mQueue != null) {
            for(Intent in : mQueue) {
                try {
                    serializeToDisk(in, openFileOutput(mInternalQueueFilePrefix + i, MODE_PRIVATE));
                } catch (FileNotFoundException e) {
                    // If we get an exception, complain about it and just move
                    // on.
                    Log.e(DEBUG_TAG, "Couldn't write queue entry to persistant storage!  Stack trace follows...");
                    e.printStackTrace();
                }
                i++;
            }
        }
        
        mServiceLooper.quit();
        
        super.onDestroy();
    }

    /**
     * Gets an iterator to the current queue.
     * 
     * @return an iterator to the current queue
     */
    public Iterator<Intent> getIterator() {
        return mQueue.iterator();
    }
    
    /**
     * Gets how many items are currently in the queue.
     * 
     * @return the number of items in the queue
     */
    public int getSize() {
        return mQueue.size();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Here's a trick I picked up from IntentService...
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
        
        // We're not sticky.  We don't want intents re-sent and we call stopSelf
        // whenever we want to stop entirely.
        return Service.START_NOT_STICKY;
    }
    
    /**
     * <p>
     * Handles the Intent sent in.  Specifically, this looks at the Intent,
     * decides if it's a command or a work unit, and then either acts on the
     * command or shoves the Intent into the queue to be processed, starting the
     * queue-working thread if need be.  This gets called on a separate thread
     * from the rest of the GUI (AND a separate thread from the queue worker).
     * The actual application-specific work happens in {@link #handleIntent(Intent)}.
     * </p>
     * 
     * @param intent the incoming Intent
     */
    private void handleCommand(Intent intent) {
        // First, check if this is a command message.
        if(intent.hasExtra(COMMAND_EXTRA)) {
            // If so, take command.  Make sure it's a valid command.
            int command = intent.getIntExtra(COMMAND_EXTRA, -1);
            
            if(!isPaused()) {
                Log.w(DEBUG_TAG, "The queue isn't paused, ignoring the command...");
                return;
            }
            
            if(command == -1) {
                // INVALID!
                Log.w(DEBUG_TAG, "Command Intent didn't have a valid command in it!");
                return;
            }
            
            if(command != COMMAND_RESUME && command != COMMAND_ABORT && command != COMMAND_RESUME_SKIP_FIRST) {
                Log.w(DEBUG_TAG, "I don't know what sort of command " + command + " is supposed to be, ignoring...");
                return;
            }

            // The thread should NOT be active right now!  If it is, we're in
            // trouble!
            if(mThread != null && mThread.isAlive()) {
                Log.e(DEBUG_TAG, "isPaused returned true, but the thread is still alive?  What?");
                // Last ditch effort: Try to interrupt the thread to death.
                mThread.interrupt();
            }
            
            mIsPaused = false;
            
            // It's a good command, send it off!
            if(command == COMMAND_RESUME) {
                // Simply restart the thread.  The queue will start from where
                // it left off.
                Log.d(DEBUG_TAG, "Restarting the thread now...");
                doNewThread();
            } else if(command == COMMAND_RESUME_SKIP_FIRST) {
                Log.d(DEBUG_TAG, "Restarting the thread now, skipping the first Intent...");
                if(mQueue.isEmpty()) {
                    Log.w(DEBUG_TAG, "The queue is empty!  There's nothing to skip!");
                } else {
                    mQueue.remove();
                }
                doNewThread();
            } else {
                // This is a COMMAND_ABORT.  Simply empty the queue (but call
                // the callback first).
                Log.d(DEBUG_TAG, "Emptying out the queue (removing " + mQueue.size() + " Intents)...");
                onQueueEmpty(false);
                mQueue.clear();
                stopSelf();
            }
        } else {
            // If this isn't a control message, add the intent to the queue.
            Log.d(DEBUG_TAG, "Enqueueing an Intent!");
            mQueue.add(intent);
            
            // Next, if the thread isn't already running (AND we're not paused),
            // make it run.  If it IS running, we'll just process the next one
            // in turn.
            if(isPaused() && resumeOnNewIntent()) {
                Log.d(DEBUG_TAG, "Queue was paused, resuming it now!");
                
                if(mThread != null && mThread.isAlive()) {
                    Log.e(DEBUG_TAG, "isPaused returned true, but the thread is still alive?  What?");
                    // Last ditch effort: Try to interrupt the thread to death.
                    mThread.interrupt();
                }
                
                mIsPaused = false;
                doNewThread();
            } else if(!isPaused() && (mThread == null || !mThread.isAlive())) {
                Log.d(DEBUG_TAG, "Starting the thread fresh...");
                doNewThread();
            }
        }
    }
    
    private void doNewThread() {
        // Only call this if the old thread isn't running.
        mThread = new Thread(new QueueThread(), "QueueService Runner");
        mThread.start();
    }

    /* (non-Javadoc)
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
    
    private class QueueThread implements Runnable {

        @Override
        public void run() {
            // Now!  Loop through the queue!
            Intent i;
            
            if(!mQueue.isEmpty())
                onQueueStart();
            
            while(!mQueue.isEmpty()) {
                i = mQueue.peek();

                Log.d(DEBUG_TAG, "Processing intent...");
                
                ReturnCode r = handleIntent(i);
                
                Log.d(DEBUG_TAG, "Intent processed, return code is " + r);
                
                // Return check!
                if(r == ReturnCode.STOP) {
                    // If the return code we got instructed us to stop entirely,
                    // wipe the queue and bail out.
                    Log.d(DEBUG_TAG, "Return said to stop, stopping now and abandoning " + mQueue.size() + " Intent(s).");
                    onQueueEmpty(false);
                    mQueue.clear();
                    stopSelf();
                    return;
                } else if(r == ReturnCode.CONTINUE) {
                    // CONTINUE means processing was a success, so we can yoink
                    // the Intent from the front of the queue and scrap it.
                    Log.d(DEBUG_TAG, "Return said to continue.");
                    mQueue.remove();
                } else if(r == ReturnCode.PAUSE) {
                    // If we were told to pause, well, pause.  We'll be told to
                    // try again later.
                    Log.d(DEBUG_TAG, "Return said to pause.");

                    mIsPaused = true;
                    onQueuePause(i);
                    return;
                }
            }
            // If we got here, then hey!  The thread's done!
            Log.d(DEBUG_TAG, "Processing complete.");
            onQueueEmpty(true);
            stopSelf();
        }
    }
    
    /**
     * Returns whether or not the queue is currently paused.
     * 
     * @return true if paused, false if not
     */
    public boolean isPaused() {
        return mIsPaused;
    }
    
    /**
     * Called whenever a new data Intent comes in and the queue is paused to
     * determine if the queue should resume immediately.  If this returns false,
     * the queue will remain paused until an explicit {@link #COMMAND_RESUME}
     * command Intent is sent.  Note that the queue will always start if the
     * queue is empty.
     *
     * @return true to resume on a new Intent, false to remain paused
     */
    protected abstract boolean resumeOnNewIntent();

    /**
     * Subclasses get this called every time something from the queue comes in
     * to be processed.  This will not be called on the main thread.  There will
     * be no callback on successful processing of an individual Intent, but
     * {@link #onQueuePause(Intent)} will be called if the queue is paused, and
     * {@link #onQueueEmpty(boolean)} will be called at the end of all processing.
     * 
     * @param i Intent to be processed
     * @return a ReturnCode indicating what the queue should do next
     */
    protected abstract ReturnCode handleIntent(Intent i);
    
    /**
     * This gets called immediately before the first Intent is processed in a
     * given run of QueueService.  That is to say, after the service is started
     * due to an Intent coming in OR every time the service is told to resume
     * after being paused.  {@link #handleIntent(Intent)} will be called after
     * this returns.  This would be a good place to set up wakelocks.
     */
    protected abstract void onQueueStart();
    
    /**
     * <p>
     * This gets called if the queue needs to be paused for some reason.  The
     * Intent that caused the pause will be included.  The thread will be killed
     * after this callback returns.  However, {@link #isPaused()} will return
     * false if called during this callback.  Try not to block it.
     * </p>
     * 
     * <p>
     * Note that you aren't doing the actual pausing here.  This method is just
     * here to do status updates or to inform the user that the queue is paused,
     * which might or might not require more input.  If you need more
     * information as to exactly why the queue was paused, you can always stuff
     * more extras in the Intent during onHandleIntent before it gets here.
     * </p>
     * 
     * <p>
     * Now would be a good time to release that wakelock you made back in
     * {@link #onQueueStart()}.
     * </p>
     * @param i Intent that caused the pause
     */
    protected abstract void onQueuePause(Intent i);
    
    /**
     * <p>
     * This is called right after the queue is done processing and right before
     * the thread is killed and isn't paused.  The boolean indicates if
     * processing was complete.  If false, it means a {@link ReturnCode#STOP}
     * was received or {@link #COMMAND_ABORT} was sent.  The queue will be
     * emptied AFTER this method returns.
     * </p>
     * 
     * <p>
     * This would be another good place to release that {@link #onQueueStart()}
     * wakelock you've been holding onto.  Onto which you've been holding.
     * </p>
     *  
     * @param allProcessed true if the queue emptied normally, false if it was
     *                     aborted before all Intents were processed
     */
    protected abstract void onQueueEmpty(boolean allProcessed);
    
    /**
     * <p>
     * Serializes the given Intent to disk for later re-reading.  Note that at
     * this point, an Intent is solely used as a means of storing data.  Which,
     * really, it can be, though I doubt that's why it was made.  This gets
     * called at onDestroy time for each Intent left in the queue (if any are
     * left at all) so that they can be recreated at onCreate time to persist
     * the Service's state (there doesn't appear to be an onSaveInstanceState
     * like you'd get with Activities).
     * </p>
     * 
     * <p>
     * Note that no checking is done to ensure you actually wrote anything to
     * the stream.  If the result is a zero-byte file, that's your
     * responsibility to handle it at deserialize time.
     * </p>
     * 
     * @param i the Intent to serialize
     * @param os what you'll be writing to
     */
    protected abstract void serializeToDisk(Intent i, OutputStream os);
    
    /**
     * Deserializes an Intent previously written to disk by serializeToDisk.
     * This will be called once for each Intent found on disk, and will be
     * called in the order of the queue.  All you have to do is pull back
     * whatever you wrote in serializeToDisk and get an Intent out of it.
     * 
     * @param is what you'll be reading from
     * @return a new Intent to be processed at the right time (if null is
     *         returned, it will be ignored)
     */
    protected abstract Intent deserializeFromDisk(InputStream is);
}
