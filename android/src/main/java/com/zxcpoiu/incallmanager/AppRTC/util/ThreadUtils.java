/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
package com.zxcpoiu.incallmanager.AppRTC;
import android.os.Looper;
import androidx.annotation.Nullable;
public class ThreadUtils {
  /**
   * Utility class to be used for checking that a method is called on the correct thread.
   */
  public static class ThreadChecker {
    @Nullable private Thread thread = Thread.currentThread();
    public void checkIsOnValidThread() {
      if (thread == null) {
        thread = Thread.currentThread();
      }
      if (Thread.currentThread() != thread) {
        throw new IllegalStateException("Wrong thread");
      }
    }
    public void detachThread() {
      thread = null;
    }
  }
  /**
   * Throws exception if called from other than main thread.
   */
  public static void checkIsOnMainThread() {
    if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
      throw new IllegalStateException("Not on main thread!");
    }
  }
}
