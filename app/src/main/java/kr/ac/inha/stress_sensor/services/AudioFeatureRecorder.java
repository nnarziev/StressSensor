package kr.ac.inha.stress_sensor.services;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import kr.ac.inha.stress_sensor.DatabaseHelper;
import kr.ac.inha.stress_sensor.Tools;

import static kr.ac.inha.stress_sensor.services.CustomSensorsService.DATA_SRC_AUDIO_LOUDNESS;

public class AudioFeatureRecorder {
    // region Constants
    public static final String TAG = "AudioFeatureRecorder";
    private final int SAMPLING_RATE = 11025;
    private final int AUDIO_BUFFER_SIZE = 1024;
    private final double SILENCE_THRESHOLD = -65.0D;
    // endregion

    // region Variables
    private boolean started;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private AudioDispatcher dispatcher;
    private DatabaseHelper db;
    // endregion

    public AudioFeatureRecorder(Context con) {
        db = new DatabaseHelper(con);
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLING_RATE, AUDIO_BUFFER_SIZE, 512);
        final SilenceDetector silenceDetector = new SilenceDetector(SILENCE_THRESHOLD, false);

        AudioProcessor mainAudioProcessor = new AudioProcessor() {

            @Override
            public void processingFinished() {
            }

            @Override
            public boolean process(AudioEvent audioEvent) {
                if (silenceDetector.currentSPL() >= -110.0D) {
                    String value = System.currentTimeMillis() + " " + silenceDetector.currentSPL() + " " + Tools.getEMAOrderFromRangeBeforeEMA(System.currentTimeMillis());
                    db.insertSensorData(DATA_SRC_AUDIO_LOUDNESS, value);
                }
                return true;
            }
        };


        if (dispatcher == null)
            Log.e(TAG, "Dispatcher is NULL: ");
        dispatcher.addAudioProcessor(silenceDetector);
        dispatcher.addAudioProcessor(mainAudioProcessor);
    }

    public void start() {
        Log.d(TAG, "Started: AudioRecorder");
        executor.execute(dispatcher);
        started = true;
    }

    public void stop() {
        Log.d(TAG, "Stopped: AudioRecorder");
        if (started) {
            dispatcher.stop();
            started = false;
        }
    }
}
