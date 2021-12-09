package com.google.firebase.perf.application;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.FrameMetricsCalculator;
import com.google.firebase.perf.util.FrameMetricsCalculator.FrameMetrics;
import com.google.firebase.perf.util.Utils;
import java.util.WeakHashMap;

public class FragmentMonitor extends FragmentManager.FragmentLifecycleCallbacks {
  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private final WeakHashMap<Fragment, Trace> fragmentToScreenTraceMap = new WeakHashMap<>();
  private final WeakHashMap<Fragment, FrameMetrics> fragmentToInitialFrameMetricsMap =
      new WeakHashMap<>();
  private final AppCompatActivity activity;
  private final Clock clock;
  private final TransportManager transportManager;
  private final AppStateMonitor appStateMonitor;

  public FragmentMonitor(AppCompatActivity activity, Clock clock, TransportManager transportManager, AppStateMonitor appStateMonitor) {
    this.activity = activity;
    this.clock = clock;
    this.transportManager = transportManager;
    this.appStateMonitor = appStateMonitor;
  }

  /**
   * Screen trace name is prefix "_st_" concatenates with Fragment's class name.
   *
   * @param fragment fragment object.
   * @return screen trace name.
   */
  public static String getFragmentScreenTraceName(Fragment fragment) {
    return Constants.SCREEN_TRACE_PREFIX + fragment.getClass().getSimpleName();
  }

  @Override
  public void onFragmentStarted(
      @NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
    System.out.println("*** Fragment started " + fragment.getClass().getSimpleName());

    Trace screenTrace =
        new Trace(
            getFragmentScreenTraceName(fragment),
            transportManager,
            clock,
            appStateMonitor);
    screenTrace.start();

    // Put parent fragments and hosting activity as attribute
    if (fragment.getParentFragment() != null) {
      screenTrace.putAttribute(Constants.PARENT_FRAGMENT_ATTRIBUTE_KEY, fragment.getParentFragment().getClass().getSimpleName());
    }
    screenTrace.putAttribute(Constants.ACTIVITY_ATTRIBUTE_KEY, activity.getClass().getSimpleName());


    fragmentToScreenTraceMap.put(fragment, screenTrace);
    FrameMetrics frameMetrics =
        FrameMetricsCalculator.calculateFrameMetrics(
            appStateMonitor.getFrameMetricsAggregator().getMetrics());
    System.out.println("*** " + fragment.getClass().getSimpleName() + " started "
        + frameMetrics.getTotalFrames()
        + " "
        + frameMetrics.getSlowFrames()
        + " "
        + frameMetrics.getFrozenFrames());

    fragmentToInitialFrameMetricsMap.put(fragment, frameMetrics);
  }

  @Override
  public void onFragmentStopped(
      @NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
    if (!fragmentToScreenTraceMap.containsKey(fragment)
        || !fragmentToInitialFrameMetricsMap.containsKey(fragment)) {
      return;
    }
    Trace screenTrace = fragmentToScreenTraceMap.get(fragment);
    FrameMetrics initialFrameMetrics = fragmentToInitialFrameMetricsMap.get(fragment);
    fragmentToScreenTraceMap.remove(fragment);
    fragmentToInitialFrameMetricsMap.remove(fragment);

    FrameMetrics curFrameMetrics =
        FrameMetricsCalculator.calculateFrameMetrics(
            appStateMonitor.getFrameMetricsAggregator().getMetrics());

    // Calculate the frames by computing the difference between the current frame metrics snapshot
    // and the initial frame metrics snapshot.
    int totalFrames = curFrameMetrics.getTotalFrames() - initialFrameMetrics.getTotalFrames();
    int slowFrames = curFrameMetrics.getSlowFrames() - initialFrameMetrics.getSlowFrames();
    int frozenFrames = curFrameMetrics.getFrozenFrames() - initialFrameMetrics.getFrozenFrames();

    System.out.println(
        "*** " + fragment.getClass().getSimpleName() + " stopped "
            + curFrameMetrics.getTotalFrames()
            + " "
            + curFrameMetrics.getSlowFrames()
            + " "
            + curFrameMetrics.getFrozenFrames());

    System.out.println(
        "*** pre: "
            + initialFrameMetrics.getTotalFrames()
            + " "
            + initialFrameMetrics.getSlowFrames()
            + " "
            + initialFrameMetrics.getFrozenFrames());


    // Only incrementMetric if corresponding metric is non-zero.
    if (totalFrames > 0) {
      screenTrace.putMetric(Constants.CounterNames.FRAMES_TOTAL.toString(), totalFrames);
    }
    if (slowFrames > 0) {
      screenTrace.putMetric(Constants.CounterNames.FRAMES_SLOW.toString(), slowFrames);
    }
    if (frozenFrames > 0) {
      screenTrace.putMetric(Constants.CounterNames.FRAMES_FROZEN.toString(), frozenFrames);
    }
    if (Utils.isDebugLoggingEnabled(activity.getApplicationContext())) {
      logger.debug(
          "*** sendScreenTrace name:"
              + getFragmentScreenTraceName(fragment)
              + " _fr_tot:"
              + totalFrames
              + " _fr_slo:"
              + slowFrames
              + " _fr_fzn:"
              + frozenFrames);
    }
    // Stop and record trace
    screenTrace.stop();
  }

  @Override
  public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                                    @NonNull View v, @Nullable Bundle savedInstanceState) {
    FrameMetrics curFrameMetrics =
        FrameMetricsCalculator.calculateFrameMetrics(
            appStateMonitor.getFrameMetricsAggregator().getMetrics());
    System.out.println(
        "*** " + f.getClass().getSimpleName() + " onFragmentViewCreatead "
            + curFrameMetrics.getTotalFrames()
            + " "
            + curFrameMetrics.getSlowFrames()
            + " "
            + curFrameMetrics.getFrozenFrames());
  }
  @Override
  public void onFragmentViewDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
    FrameMetrics curFrameMetrics =
        FrameMetricsCalculator.calculateFrameMetrics(
            appStateMonitor.getFrameMetricsAggregator().getMetrics());
    System.out.println(
        "*** " + f.getClass().getSimpleName() + " onFragmentViewDestroyed "
            + curFrameMetrics.getTotalFrames()
            + " "
            + curFrameMetrics.getSlowFrames()
            + " "
            + curFrameMetrics.getFrozenFrames());
  }

}