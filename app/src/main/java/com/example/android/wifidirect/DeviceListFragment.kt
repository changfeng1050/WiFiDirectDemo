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

import android.app.ProgressDialog
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.ListFragment
import java.util.*

/**
 * A ListFragment that displays available peers on discovery and requests the
 * parent activity to handle user interaction events
 */
class DeviceListFragment : ListFragment(), PeerListListener {

    private val peers = ArrayList<WifiP2pDevice>()
    private var progressDialog: ProgressDialog? = null
    private var mContentView: View? = null
    /**
     * @return this device
     */
    var device: WifiP2pDevice? = null
        private set

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        this.listAdapter = WiFiPeerListAdapter(requireActivity(), R.layout.row_devices, peers)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mContentView = inflater.inflate(R.layout.device_list, null)
        return mContentView
    }

    private fun getDeviceStatus(deviceStatus: Int): String {
        Log.d(WiFiDirectActivity.TAG, "Peer status :$deviceStatus")
        when (deviceStatus) {
            WifiP2pDevice.AVAILABLE -> return "Available"
            WifiP2pDevice.INVITED -> return "Invited"
            WifiP2pDevice.CONNECTED -> return "Connected"
            WifiP2pDevice.FAILED -> return "Failed"
            WifiP2pDevice.UNAVAILABLE -> return "Unavailable"
            else -> return "Unknown"
        }
    }

    /**
     * Initiate a connection with the peer.
     */
    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val device = listAdapter!!.getItem(position) as WifiP2pDevice
        (activity as DeviceActionListener).showDetails(device)
    }

    /**
     * Array adapter for ListFragment that maintains WifiP2pDevice list.
     */
    private inner class WiFiPeerListAdapter
    /**
     * @param context
     * @param textViewResourceId
     * @param objects
     */
    (context: Context, textViewResourceId: Int,
     private val items: List<WifiP2pDevice>) : ArrayAdapter<WifiP2pDevice>(context, textViewResourceId, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var v = convertView
            if (v == null) {
                val vi = requireActivity().getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                v = vi.inflate(R.layout.row_devices, null)
            }
            val device = items[position]
            if (device != null) {
                val top = v!!.findViewById<View>(R.id.device_name) as TextView
                val bottom = v.findViewById<View>(R.id.device_details) as TextView
                if (top != null) {
                    top.text = device.deviceName
                }
                if (bottom != null) {
                    bottom.text = getDeviceStatus(device.status)
                }
            }

            return v!!

        }
    }

    /**
     * Update UI for this device.
     *
     * @param device WifiP2pDevice object
     */
    fun updateThisDevice(device: WifiP2pDevice) {
        this.device = device
        var view = mContentView!!.findViewById<View>(R.id.my_name) as TextView
        view.text = device.deviceName
        view = mContentView!!.findViewById<View>(R.id.my_status) as TextView
        view.text = getDeviceStatus(device.status)
    }

    override fun onPeersAvailable(peerList: WifiP2pDeviceList) {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }
        peers.clear()
        peers.addAll(peerList.deviceList)
        (listAdapter as WiFiPeerListAdapter).notifyDataSetChanged()
        if (peers.size == 0) {
            Log.d(WiFiDirectActivity.TAG, "No devices found")
            return
        }

    }

    fun clearPeers() {
        peers.clear()
        (listAdapter as WiFiPeerListAdapter).notifyDataSetChanged()
    }

    /**
     *
     */
    fun onInitiateDiscovery() {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }
        progressDialog = ProgressDialog.show(activity, "Press back to cancel", "finding peers", true,
                true) { }
    }

    /**
     * An interface-callback for the activity to listen to fragment interaction
     * events.
     */
    interface DeviceActionListener {

        fun showDetails(device: WifiP2pDevice)

        fun cancelDisconnect()

        fun connect(config: WifiP2pConfig)

        fun disconnect()
    }

}
