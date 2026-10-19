package com.graphlab.work;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.graphlab.data.content.ContentSync;
import com.graphlab.engine.content.ContentException;
import java.io.IOException;

/**
 * Fetches the hosted question cards and puzzle levels (MS7). A network problem is retried a few times with
 * WorkManager's back-off; a file that downloads but is not valid is dropped until tomorrow's run, because retrying
 * would only download the same bad file.
 */
public final class ContentRefreshWorker extends Worker {
    private static final int MAX_ATTEMPTS = 3;

    public ContentRefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            ContentSync.refresh(getApplicationContext());
            return Result.success();
        } catch (IOException network) {
            return getRunAttemptCount() < MAX_ATTEMPTS ? Result.retry() : Result.success();
        } catch (ContentException | RuntimeException invalid) {
            return Result.success();
        }
    }
}
