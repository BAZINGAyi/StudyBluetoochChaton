/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.example.bazinga.bluetoothchaton;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Set;

// 这个 activity 主要用于显示成对话框，用于查找蓝牙和添加蓝牙

public class DeviceListActivity extends Activity {
    // 调试相关
    private static final String TAG = "DeviceListActivity";

    private static final boolean D = true;


    // 返回给 mainactivity 中的键, mainActivity 根据这个地址获取蓝牙设备
    public static String EXTRA_DEVICE_ADDRESS = "device_address";


    // 页面显示
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;

    private ArrayAdapter<String> mNewDevicesArrayAdapter;


    // 蓝牙适配器
    private BluetoothAdapter mBtAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.device_list);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        // Initialize the button to perform device discovery
        Button scanButton = (Button) findViewById(R.id.button_scan);

        // 查找未配对的蓝牙设备
        scanButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {

                doDiscovery();

                v.setVisibility(View.GONE);

            }
        });

        // 初始化配对的 listview 的adapter
        // 初始化未配对的 listview 的adapter
        mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

        mNewDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);


        // 配对的 listview 绑定
        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);

        pairedListView.setAdapter(mPairedDevicesArrayAdapter);

        pairedListView.setOnItemClickListener(mDeviceClickListener);



        // 未配对的 listview 绑定
        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);

        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);

        newDevicesListView.setOnItemClickListener(mDeviceClickListener);



        // 注册发现新的蓝牙设备的广播接收器
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);

        this.registerReceiver(mReceiver, filter);



        // 注册蓝牙发现完成后的广播接受器
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        this.registerReceiver(mReceiver, filter);


        // 获取 蓝牙的adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // 得到已配对的蓝牙设备列表
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        // 进行显示
        if (pairedDevices.size() > 0) {

            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);

            for (BluetoothDevice device : pairedDevices) {

                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());

            }
        } else {

            String noDevices = getResources().getText(R.string.none_paired).toString();

            mPairedDevicesArrayAdapter.add(noDevices);

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 取消发现蓝牙
        if (mBtAdapter != null) {

            mBtAdapter.cancelDiscovery();

        }
        // 取消广播接收器的注册
        this.unregisterReceiver(mReceiver);
    }

    // 发现蓝牙设备
    private void doDiscovery() {
        if (D) Log.d(TAG, "doDiscovery()");

        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);

        // 显示标题
        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        // 如果蓝牙在发现设备，则取消发现
        if (mBtAdapter.isDiscovering()) {

            mBtAdapter.cancelDiscovery();

        }

        mBtAdapter.startDiscovery();
    }

    // 点击已配对的设备，再 mainactivity 中直接连接即可
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {

        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            // 取消发现
            mBtAdapter.cancelDiscovery();

            // 获取蓝牙地址
            String info = ((TextView) v).getText().toString();

            String address = info.substring(info.length() - 17);


            // 放入数据
            Intent intent = new Intent();

            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            setResult(Activity.RESULT_OK, intent);

            finish();
        }
    };

   // 处理发现蓝牙的请求和发现蓝牙结束的请求
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            // 处理发现蓝牙
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // 如果没有配对，则加入到新发现的 adapter 中
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {

                    mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());

                }
            // 处理发现蓝牙结束
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {

                setProgressBarIndeterminateVisibility(false);

                setTitle(R.string.select_device);

                if (mNewDevicesArrayAdapter.getCount() == 0) {

                    String noDevices = getResources().getText(R.string.none_found).toString();

                    mNewDevicesArrayAdapter.add(noDevices);

                }
            }
        }
    };

}

