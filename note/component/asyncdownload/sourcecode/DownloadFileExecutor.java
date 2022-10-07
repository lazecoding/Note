
import java.util.concurrent.*;

/**
 * 文件下载缓冲池执行器
 *
 */
public class DownloadFileExecutor {
    /**
     * 核心数量
     */
    private static final int nThreads = Runtime.getRuntime().availableProcessors();

    /**
     * 延迟任务执行器
     */
    private static ScheduledExecutorService downloadFileDelayExecutor = new ScheduledThreadPoolExecutor(nThreads);

    /**
     * 异步任务执行器
     */
    private static ThreadPoolExecutor downloadFileAsynExecutor = new ThreadPoolExecutor(nThreads, nThreads, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    /**
     * 延迟时间
     */
    private static final long DELAYTIME = 60 * 10;

    /**
     * 延迟队列
     *
     * @param task 待执行任务
     */
    public static void doDelayTask(Runnable task) {
        downloadFileAsynExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                task.run();
            }
        }, delayTime, TimeUnit.SECONDS);
;
    }

    /**
     * 异步任务
     *
     * @param task 待执行任务
     */
    public static void asynTask(Runnable task) {
        downloadFileAsynExecutor.execute(task);
    }

}