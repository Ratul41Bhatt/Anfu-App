package com.hjq.demo.utils;

import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

public class TouchHelper {

   /**
    * 在给定的Activity上模拟完整的手指触摸操作
    *
    * @param activity 要触发点击事件的 Activity
    * @param x        起始触摸的 x 坐标（相对于屏幕）
    * @param y        起始触摸的 y 坐标（相对于屏幕）
    */
   public static void simulateFullTouch(Activity activity, float centerX, float centerY) {
      View rootView = activity.getWindow().getDecorView().getRootView();

      long downTime = SystemClock.uptimeMillis();
      long eventTime = downTime;
      float radius  = 100;
      // 画圆的步骤
      int steps = 100; // 圆的细分数，可以调整
      for (int i = 0; i <= steps; i++) {
         // 计算当前点的角度
         double angle = 2 * Math.PI * i / steps; // 从0到2π
         float x = (float) (centerX + radius * Math.cos(angle));
         float y = (float) (centerY + radius * Math.sin(angle));

         // 创建 ACTION_MOVE 事件
         MotionEvent actionMove = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0);
         rootView.dispatchTouchEvent(actionMove);
         eventTime += 10; // 控制绘制的速度，可以调整
      }

      // 创建 ACTION_DOWN 事件
      MotionEvent actionDown = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, centerX, centerY, 0);
      rootView.dispatchTouchEvent(actionDown);

      // 创建 ACTION_UP 事件
      MotionEvent actionUp = MotionEvent.obtain(eventTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, centerX, centerY, 0);
      rootView.dispatchTouchEvent(actionUp);

      // 回收 MotionEvent 对象
      actionDown.recycle();
      actionUp.recycle();
   }

}

