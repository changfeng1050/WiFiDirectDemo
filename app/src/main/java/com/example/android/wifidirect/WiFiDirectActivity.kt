/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wifidirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.*
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.android.wifidirect.DeviceListFragment.DeviceActionListener

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
class WiFiDirectActivity : AppCompatActivity(), ChannelListener, DeviceActionListener {
    private var manager: WifiP2pManager? = null
    private var isWifiP2pEnabled = false
    private var retryChannel = false

    private val intentFilter = IntentFilter()
    private var channel: Channel? = null
    private var receiver: BroadcastReceiver? = null

    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    fun setIsWifiP2pEnabled(isWifiP2pEnabled: Boolean) {
        this.isWifiP2pEnabled = isWifiP2pEnabled
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        // add necessary intent values to be matched.

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager!!.initialize(this, mainLooper, null)
    }

    /** register the BroadcastReceiver with the intent values to be matched  */
    public override fun onResume() {
        super.onResume()
        receiver = WiFiDirectBroadcastReceiver(manager, channel!!, this)
        registerReceiver(receiver, intentFilter)
    }

    public override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    fun resetData() {
        val fragmentList = fragmentManager
                .findFragmentById(R.id.frag_list) as DeviceListFragment
        val fragmentDetails = fragmentManager
                .findFragmentById(R.id.frag_detail) as DeviceDetailFragment
        fragmentList?.clearPeers()
        fragmentDetails?.resetViews()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.action_items, menu)
        return true
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.atn_direct_enable -> {
                if (manager != null && channel != null) {

                    // Since this is the system wireless settings activity, it's
                    // not going to send us a result. We will be notified by
                    // WiFiDeviceBroadcastReceiver instead.

                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                } else {
                    Log.e(TAG, "channel or manager is null")
                }
                return true
            }

            R.id.atn_direct_discover -> {
                if (!isWifiP2pEnabled) {
                    Toast.makeText(this@WiFiDirectActivity, R.string.p2p_off_warning,
                            Toast.LENGTH_SHORT).show()
                    return true
                }
                val fragment = fragmentManager
                        .findFragmentById(R.id.frag_list) as DeviceListFragment
                fragment.onInitiateDiscovery()
                manager!!.discoverPeers(channel, object : WifiP2pManager.ActionListener {

                    override fun onSuccess() {
                        Toast.makeText(this@WiFiDirectActivity, "Discovery Initiated",
                                Toast.LENGTH_SHORT).show()
                    }

                    override fun onFailure(reasonCode: Int) {
                        Toast.makeText(this@WiFiDirectActivity, "Discovery Failed : $reasonCode",
                                Toast.LENGTH_SHORT).show()
                    }
                })
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun showDetails(device: WifiP2pDevice) {
        val fragment = fragmentManager
                .findFragmentById(R.id.frag_detail) as DeviceDetailFragment
        fragment.showDetails(device)

    }

    override fun connect(config: WifiP2pConfig) {
        manager!!.connect(channel, config, object : ActionListener {

            override fun onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            override fun onFailure(reason: Int) {
                Toast.makeText(this@WiFiDirectActivity, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun disconnect() {
        val fragment = fragmentManager
                .findFragmentById(R.id.frag_detail) as DeviceDetailFragment
        fragment.resetViews()
        manager!!.removeGroup(channel, object : ActionListener {

            override fun onFailure(reasonCode: Int) {
                Log.d(TAG, "Disconnect failed. Reason :$reasonCode")

            }

            override fun onSuccess() {
                fragment.view!!.visibility = View.GONE
            }

        })
    }

    override fun onChannelDisconnected() {
        // we will try once more
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show()
            resetData()
            retryChannel = true
            manager!!.initialize(this, mainLooper, this)
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show()
        }
    }

    override fun cancelDisconnect() {

        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (manager != null) {
            val fragment = supportFragmentManager
                    .findFragmentById(R.id.frag_list) as DeviceListFragment
            if (fragment.device == null || fragment.device!!.status == WifiP2pDevice.CONNECTED) {
                disconnect()
            } else if (fragment.device!!.status == WifiP2pDevice.AVAILABLE || fragment.device!!.status == WifiP2pDevice.INVITED) {

                manager!!.cancelConnect(channel, object : ActionListener {

                    override fun onSuccess() {
                        Toast.makeText(this@WiFiDirectActivity, "Aborting connection",
                                Toast.LENGTH_SHORT).show()
                    }

                    override fun onFailure(reasonCode: Int) {
                        Toast.makeText(this@WiFiDirectActivity,
                                "Connect abort request failed. Reason Code: $reasonCode",
                                Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }

    }

    companion object {
        const val TAG = "wifidirectdemo"
    }
}
