package com.example.bazinga.bluetoothchaton;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


/**
 * Created by bazinga on 2017/3/22.
 */
public class BluetoothChatService {

    // BluetoothSocket 的传递过程分为两种情况
    // 1. 作为服务端，通过 BluetoothServerSocket 的 accept() 函数获取 BluetoothSocket
    // 然后传递给 ConnectedThread
    // 2. 作为客户端，连接其它蓝牙的设备的服务端，通过 device.createRfcommSocketToServiceRecord
    // 获取BluetoothSocket， 然后传递给 ConnectedThread
    // 注意传递完后就关闭自己的 socket，因为不在需要了，传递的过程是复制，操作的不是同一个
    // socket()

    // 调试相关
    private static final String TAG = "BluetoothChatService";

    private static final boolean D = true;


    // 蓝牙的四种状态
    // 没有发现蓝牙
    public static final int STATE_NONE = 0;

    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    // 初始化连接外部蓝牙的操作
    public static final int STATE_CONNECTING = 2;
    // 已经连接了外部的蓝牙
    public static final int STATE_CONNECTED = 3;
    // 显示当前连接的状态
    private int mState;


    // 蓝牙连接套接字的名称
    private static final String NAME_SECURE = "BluetoothChatSecure";

    // 蓝牙连接的标示 UUID
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a6a");


    // 成员变量
    private final BluetoothAdapter mAdapter;
    // 用于给 Mainactivity 中的 handler 发送消息
    private final Handler mHandler;
    // 作为接受连接的服务器
    private AcceptThread mSecureAcceptThread;
    // 发起蓝牙连接请求
    private ConnectThread mConnectThread;
    // 管理已经连接的蓝牙
    private ConnectedThread mConnectedThread;


    public BluetoothChatService(Context context, Handler handler) {

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        mState = STATE_NONE;

        mHandler = handler;

    }


    private synchronized void setState(int state) {

        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);

        mState = state;

        // 给 MainActivity 的 mhandler 发送蓝牙状态的更新消息，用于更新UI
        mHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();

    }

    // 返回当前蓝牙的连接状态
    public synchronized int getState() {
        return mState;
    }

    // 作为接受连接请求的服务器，直到接受到一个连接，或者被取消
    // 注意这里作为服务器进行连接，假如有设备申请连接，直接进行连接，不需要和客户端一样再去
    // 执行 connect() 操作
    private class AcceptThread extends Thread {

        private final BluetoothServerSocket mmServerSocket;

        private String mSocketType;

        public AcceptThread(Boolean bool) {

            BluetoothServerSocket tmp = null;

            // 创建套接字
            try {

                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE);

            } catch (IOException e) {

                 Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);

            }

            mmServerSocket = tmp;
        }

        public void run() {

            if (D) Log.d(TAG, "Socket Type: " + mSocketType +
                   "BEGIN mAcceptThread" + this);

            // 设置当前线程的名字
             setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

            while (mState != STATE_CONNECTED) {
                try {

                    // accept() 为阻塞函数，只有连接成功或者出现异常才返回
                    socket = mmServerSocket.accept();

                } catch (IOException e) {

                     Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);

                    break;

                }

                // 如果连接成功
                if (socket != null) {

                    synchronized (BluetoothChatService.this) {

                        switch (mState) {

                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // 连接成功，开始管理连接的 connected 线程
                                // 不需要再进行，像客户端一样的发送连接
                                connected(socket, socket.getRemoteDevice(), mSocketType);

                                break;

                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // 理解的不太好
                                // Either not ready or already connected. Terminate new socket.
                                try {

                                    socket.close();

                                } catch (IOException e) {

                                     Log.e(TAG, "Could not close unwanted socket", e);

                                }
                                break;
                        }
                    }
                }
            }

            if (D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);
        }

        public void cancel() {

             if (D) Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);

            try {
                //关闭服务器的连接
                mmServerSocket.close();

            } catch (IOException e) {

                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);

            }

        }

    }

    // 作为客户端向服务端发起连接的线程
    private class ConnectThread extends Thread {

        private final BluetoothSocket mmSocket;

        private final BluetoothDevice mmDevice;

        private String mSocketType;

        public ConnectThread(BluetoothDevice bluetoothDevice, boolean secure) {

            mmDevice = bluetoothDevice;

            BluetoothSocket tmp = null;

            // 得到远程的连接设备上得到连接

            try {

                tmp = bluetoothDevice.createInsecureRfcommSocketToServiceRecord( //新版本连接方法
                        MY_UUID_SECURE);


            } catch (IOException e) {

                  Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);

            }

            mmSocket = tmp;

        }

        public void run() {

            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);

            setName("ConnectThread" + mSocketType);

            // 已经连接上设备 关闭发现新设备
            mAdapter.cancelDiscovery();

            // 进行蓝牙的连接
            try {
                // 成功返回一个连接
                mmSocket.connect();

            } catch (IOException e) {
                // 连接失败关闭 socket
                try {

                    mmSocket.close();

                } catch (IOException e2) {

                       Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                // 向 MainActvity中发送消息 提示用户连接失败
                connectionFailed();

                return;
            }

            // 设置连接蓝牙为空，因为我们已经连接成功了
            synchronized (BluetoothChatService.this) {

                mConnectThread = null;

            }

            // 开始管理蓝牙的操作
            connected(mmSocket, mmDevice, mSocketType);

        }

        public void cancel() {

            try {

                mmSocket.close();

            } catch (IOException e) {

                  Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);

            }
        }

    }


    private void connectionFailed() {
        // 向主UI发送消息
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);

        Bundle bundle = new Bundle();

        bundle.putString(MainActivity.TOAST, "无法连接设备");

        msg.setData(bundle);

        mHandler.sendMessage(msg);

        // 重启蓝牙的所有服务
        BluetoothChatService.this.start();
    }


    // 用于开启管理蓝牙的操作
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device,
                                       final String socketType) {

        if (D) Log.d(TAG, "connected, Socket Type:" + socketType);

        // 关闭已经完成的连接操作
        if (mConnectThread != null) {

            mConnectThread.cancel();

            mConnectThread = null;
        }

        // 关闭任何当前正在运行的连接
        if (mConnectedThread != null) {

            mConnectedThread.cancel();

            mConnectedThread = null;
        }

        //  关闭服务端的服务，因为当前只需要连接一个设备
        if (mSecureAcceptThread != null) {

            mSecureAcceptThread.cancel();

            mSecureAcceptThread = null;
        }


        // 开启管理蓝牙的服务 进行通信
        mConnectedThread = new ConnectedThread(socket, socketType);

        mConnectedThread.start();

        // 给 MainActivity 发送消息，通知改变 UI 已经连接到蓝牙设备
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);

        Bundle bundle = new Bundle();

        bundle.putString(MainActivity.DEVICE_NAME, device.getName());

        msg.setData(bundle);

        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);

    }

    // 开始连接蓝牙
    public synchronized void connect(BluetoothDevice device, boolean secure) {

        if (D) Log.d(TAG, "connect to: " + device);

        // 关闭连接蓝牙的线程
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // 关闭已经连接的蓝牙线程
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // 开始连接蓝牙设备
        mConnectThread = new ConnectThread(device, secure);

        mConnectThread.start();

        setState(STATE_CONNECTING);
    }

    // 管理蓝牙通信
    // 其它线程如 acceptThread,connectThrad 没有作用了关闭就好
    private class ConnectedThread extends Thread {

        private final BluetoothSocket mmSocket;

        private final InputStream mmInStream;

        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) {

             Log.d(TAG, "create ConnectedThread: " + socketType);

            mmSocket = socket;

            InputStream tmpIn = null;

            OutputStream tmpOut = null;

            // 获取输入输出流
            try {

                tmpIn = socket.getInputStream();

                tmpOut = socket.getOutputStream();

            } catch (IOException e) {

                 Log.e(TAG, "temp sockets not created", e);

            }

            mmInStream = tmpIn;

            mmOutStream = tmpOut;
        }


        public void run() {

             Log.i(TAG, "BEGIN mConnectedThread");

            byte[] buffer = new byte[1024];

            int bytes;

            // 一直监听输入流的信息
            while (mState == STATE_CONNECTED) {

                try {

                    bytes = mmInStream.read(buffer);
                    // 读取完成向 MainActivity 发送消息，更新 UI

                    mHandler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();

                } catch (IOException e) {

                     Log.e(TAG, "disconnected", e);

                    connectionLost();

                    BluetoothChatService.this.start();

                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {

                mmOutStream.write(buffer);

                // 写入完成向 MainActivity 发送消息，更新 UI
                mHandler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();

            } catch (IOException e) {

                 Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {

                mmSocket.close();

            } catch (IOException e) {

                  Log.e(TAG, "close() of connect socket failed", e);
            }
        }

    }

    private void connectionLost() {
        // 向 MainActivity 发送消息 , 连接丢失
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);

        Bundle bundle = new Bundle();

        bundle.putString(MainActivity.TOAST, "Device connection was lost");

        msg.setData(bundle);

        mHandler.sendMessage(msg);

        //BluetoothChatService.this.start();

    }

    // 开启服务端的程序
    public synchronized void start() {
       // if (D) Log.d(TAG, "start");

        // 取消连接蓝牙设备的线程
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // 取消已经连接的蓝牙线程
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        setState(STATE_LISTEN);

        // 开启服务端的程序
        if (mSecureAcceptThread == null) {

            mSecureAcceptThread = new AcceptThread(true);

            mSecureAcceptThread.start();
        }

    }

    // 停止所有蓝牙服务
    public synchronized void stop() {

        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {

            mConnectThread.cancel();

            mConnectThread = null;
        }

        if (mConnectedThread != null) {

            mConnectedThread.cancel();

            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {

            mSecureAcceptThread.cancel();

            mSecureAcceptThread = null;
        }

        setState(STATE_NONE);
    }

    // 提供给 MainActivity 写入的信息的接口
    public  void write(byte[] out) {

        ConnectedThread r;

        synchronized (this) {

            if (mState != STATE_CONNECTED) return;

            r = mConnectedThread;

        }

            r.write(out);

    }
}