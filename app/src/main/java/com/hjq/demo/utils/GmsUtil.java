package com.hjq.demo.utils;
import android.content.Context;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

public class GmsUtil {

   public static boolean isGmsAvailable(Context context) {
      GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
      int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
      return resultCode == ConnectionResult.SUCCESS;
   }
}
