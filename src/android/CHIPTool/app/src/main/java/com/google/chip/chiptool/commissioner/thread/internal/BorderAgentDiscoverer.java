/*
 *   Copyright (c) 2020 Project CHIP Authors
 *   All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package com.google.chip.chiptool.commissioner.thread.internal;

import android.Manifest.permission;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import androidx.annotation.RequiresPermission;
import com.google.chip.chiptool.commissioner.thread.BorderAgentInfo;
import java.util.Map;

class BorderAgentDiscoverer implements NsdManager.DiscoveryListener {

  private static final String TAG = BorderAgentDiscoverer.class.getSimpleName();

  private static final String SERVICE_TYPE = "_meshcop._udp";
  private static final String KEY_DISCRIMINATOR = "discriminator";
  private static final String KEY_NETWORK_NAME = "nn";
  private static final String KEY_EXTENDED_PAN_ID = "xp";

  private WifiManager.MulticastLock wifiMulticastLock;
  private NsdManager nsdManager;
  private BorderAgentListener borderAgentListener;

  public interface BorderAgentListener {
    void onBorderAgentFound(BorderAgentInfo borderAgentInfo);

    void onBorderAgentLost(BorderAgentInfo borderAgentInfo);
  }

  @RequiresPermission(permission.INTERNET)
  public BorderAgentDiscoverer(Context context, BorderAgentListener borderAgentListener) {
    WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    wifiMulticastLock = wifi.createMulticastLock("multicastLock");

    nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);

    this.borderAgentListener = borderAgentListener;
  }

  public void start() {
    wifiMulticastLock.setReferenceCounted(true);
    wifiMulticastLock.acquire();

    nsdManager.discoverServices(
        BorderAgentDiscoverer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, this);
  }

  public void stop() {
    nsdManager.stopServiceDiscovery(this);

    if (wifiMulticastLock != null) {
      wifiMulticastLock.release();
      wifiMulticastLock = null;
    }
  }

  @Override
  public void onDiscoveryStarted(String serviceType) {
    Log.d(TAG, "start discovering Border Agent");
  }

  @Override
  public void onDiscoveryStopped(String serviceType) {
    Log.d(TAG, "stop discovering Border Agent");
  }

  @Override
  public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
    Log.d(TAG, "a Border Agent service found");

    nsdManager.resolveService(
        nsdServiceInfo,
        new NsdManager.ResolveListener() {
          @Override
          public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.e(
                TAG,
                String.format(
                    "failed to resolve service %s, error: %d", serviceInfo.toString(), errorCode));
          }

          @Override
          public void onServiceResolved(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "successfully resolved service: " + serviceInfo.toString());
            borderAgentListener.onBorderAgentFound(getBorderAgentInfo(serviceInfo));
          }
        });
  }

  @Override
  public void onServiceLost(NsdServiceInfo nsdServiceInfo) {
    Log.d(TAG, "a Border Agent service is gone");
    borderAgentListener.onBorderAgentLost(getBorderAgentInfo(nsdServiceInfo));
  }

  @Override
  public void onStartDiscoveryFailed(String serviceType, int errorCode) {
    Log.d(TAG, "start discovering Border Agent failed: " + errorCode);
  }

  @Override
  public void onStopDiscoveryFailed(String serviceType, int errorCode) {
    Log.d(TAG, "stop discovering Border Agent failed: " + errorCode);
  }

  private BorderAgentInfo getBorderAgentInfo(NsdServiceInfo serviceInfo) {
    Map<String, byte[]> attrs = serviceInfo.getAttributes();
    String discriminator = "CC11BB22";

    if (attrs.containsKey(KEY_DISCRIMINATOR)) {
      discriminator = new String(attrs.get(KEY_DISCRIMINATOR));
    }

    return new BorderAgentInfo(
        discriminator,
        new String(attrs.get(KEY_NETWORK_NAME)),
        attrs.get(KEY_EXTENDED_PAN_ID),
        serviceInfo.getHost(),
        serviceInfo.getPort());
  }
}
