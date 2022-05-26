import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class JacocoInstrumentation extends Instrumentation implements FinishListener {

    public static String TAG = "JacocoInstrumentation:";
    private static String DEFAULT_COVERAGE_FILE_PATH;
    private static final boolean LOGD = true;
    private boolean mCoverage = true;
    private String mCoverageFilePath;

    public JacocoInstrumentation(String defaultCoverageFilePath) {
        DEFAULT_COVERAGE_FILE_PATH = defaultCoverageFilePath;
    }

    @Override
    public void onCreate(Bundle arguments) {
        Log.d(TAG, "onCreate(" + arguments + ")");
        super.onCreate(arguments);

        if(arguments != null) {
            mCoverage = getBooleanArgument(arguments, "coverage");
            mCoverageFilePath = arguments.getString("coverageFile");
        }
        start();
    }

    private boolean getBooleanArgument(Bundle arguments, String tag) {
        String tagString = arguments.getString(tag);
        return tagString != null && Boolean.parseBoolean(tagString);
    }


    private String getCoverageFilePath() {
        if (mCoverageFilePath == null) {
            return DEFAULT_COVERAGE_FILE_PATH;
        } else {
            return mCoverageFilePath;
        }
    }

    // use java reflection to dump coverage report
    private void generateCoverageReport() {
        Log.d(TAG, "generateCoverageReport():" + getCoverageFilePath());
        OutputStream out = null;
        try {
            out = new FileOutputStream(getCoverageFilePath(), false);
            Object agent = Class.forName("org.jacoco.agent.rt.RT")
                    .getMethod("getAgent")
                    .invoke(null);
            //https://www.jacoco.org/jacoco/trunk/doc/api/index.html
            //reset - if true the current execution data is cleared afterwards
            out.write((byte[]) agent.getClass().getMethod("getExecutionData", boolean.class)
                    .invoke(agent, true));
        } catch (Exception e) {
            Log.d(TAG, e.toString(), e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // set the coverage file path
    private boolean setCoverageFilePath(String filePath){
        if(filePath != null && filePath.length() > 0) {
            mCoverageFilePath = filePath;
            return true;
        }
        return false;
    }

    @Override
    public void dumpIntermediateCoverage(String filePath){
        if(LOGD){
            Log.d(TAG,"Intermediate Dump Called with file name :"+ filePath);
        }
        if(mCoverage){
            if(!setCoverageFilePath(filePath)){
                if(LOGD){
                    Log.d(TAG,"Unable to set the given file path:"+filePath+" as dump target.");
                }
            }
            generateCoverageReport();
            setCoverageFilePath(DEFAULT_COVERAGE_FILE_PATH);
        }
    }
}
