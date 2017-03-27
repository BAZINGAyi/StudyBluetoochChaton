package com.example.bazinga.bluetoothchaton;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    // log
    private static final String TAG = "BlurtiithChat";

    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    // 从蓝牙未连接到蓝牙已成功连接中的状态变化 用 BluetoothChatService 的状态进行判断
    public static final int MESSAGE_STATE_CHANGE = 1;

    public static final int MESSAGE_READ = 2;

    public static final int MESSAGE_WRITE = 3;

    public static final int MESSAGE_DEVICE_NAME = 4;

    public static final int MESSAGE_TOAST = 5;


    // send Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;

    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;

    private static final int LOCAL_BLUETOOTH_DEVICE_STATE = 3;


    // Layout Views
    private ListView mConversationView;

    private EditText mOutEditText;

    private Button mSendBUtton;

    // 用于获取蓝牙设备名字的键
    public static final String DEVICE_NAME = "device_name";
    // 用于获取蓝牙状态改变的键
    public static final String TOAST = "toast";

    // Name of the connected device
    private String mConnectedDEviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter;
    private BluetoothAdapter mBLuetoothAdapter = null;
    // Member object for the chat services
    // 将所有蓝牙的操作封装到 BluetoothChatService 中，降低类之间的耦合
    private BluetoothChatService mChatService = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        // 获取本地的蓝牙适配器，开始蓝牙的基本任务，例如发现蓝牙设备，查看已
        // 配对的蓝牙设备，监听其它设备等功能，是连接蓝牙的基础
        mBLuetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        //如果没有获取到证明，设备不支持蓝牙
        if (mBLuetoothAdapter == null) {

            Toast.makeText(this, "蓝牙设备不存在", Toast.LENGTH_LONG).show();

            finish();

            return;
        }
    }

    // onStart() 方法开始对于用户才是可见的
    @Override
    protected void onStart() {

        super.onStart();

        // 判断蓝牙是否开启
        if (!mBLuetoothAdapter.isEnabled()) {

            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

            startActivityForResult(enableIntent, LOCAL_BLUETOOTH_DEVICE_STATE);

        } else {

            if (mChatService == null) setupChat();
        }
    }

    private void setupChat() {

        // 初始化显示聊天的 listview 的列表
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);

        mConversationView = (ListView) findViewById(R.id.listView_chatOn);

        mConversationView.setAdapter(mConversationArrayAdapter);

        // 初始化发送消息输入框,和监听键盘
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);

        mOutEditText.setOnEditorActionListener(mInputMessageListener);

        // 初始化发送按钮并设置点击事件
        mSendBUtton = (Button) findViewById(R.id.button_send);

        mSendBUtton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // 为什么要重新绑定和获取输入框中的信息
                TextView view = (TextView) findViewById(R.id.edit_text_out);

                String message = view.getText().toString();

                sendMessage(message);

            }
        });

        // 初始化蓝牙服务，建立蓝牙连接,
        // mHandler 的作用是得到从 BluetoothChatService 发来的消息，并作出处理
        mChatService = new BluetoothChatService(this, mHandler);

        // 初始化输出 message 的缓冲区
        mOutStringBuffer = new StringBuffer("");

    }

    private TextView.OnEditorActionListener mInputMessageListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            // 如果按下的是键盘上的回车键，并且回车键松开则发送消息，注意 return true 才发送消息
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {

                String message = v.getText().toString();

                sendMessage(message);

            }

            return true;
        }
    };

    private void sendMessage(String message) {

        // 只有在已经连接的状态才能正常通信
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {

            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();

            return;

        }

        if (message.length() > 0) {

            byte[] send = message.getBytes();

            mChatService.write(send);

            // 置空EditText
            mOutStringBuffer.setLength(0);

            mOutEditText.setText(mOutStringBuffer);
        }

    }

    // onResume() 方法开始执行时，用户才可以与之交互
    @Override
    protected void onResume() {

        super.onResume();

        if (mChatService != null) {

            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {

                mChatService.start();
            }
        }
    }

    // onResume() 到 onPause() 称为foreground lifetime
    @Override
    protected void onPause() {

        super.onPause();

        if(D) Log.e(TAG, "- ON PAUSE -");

    }

    // 从onStart() 到 onStop() 称为visible lifetime
    @Override
    protected void onStop() {

        super.onStop();

        if(D) Log.e(TAG, "-- ON STOP --");

    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (mChatService != null) mChatService.stop();

        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    // 处理 BluetoothChatService 发来的消息
    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {

                // 蓝牙的状态改变
                case MESSAGE_STATE_CHANGE:

                    switch (msg.arg1) {

                        case BluetoothChatService.STATE_CONNECTED:

                            // 设置标题栏的显示状态和连接设备的名字
                            // getString() 用于替换本地化的字符串中一部分
                            // 在 string.xml 下定义命名空间后，可以用于替换。 xliff 的使用
                            setStatus(getString(R.string.title_connected_to, mConnectedDEviceName));
                            // 将 adapter 中的数据清空 准备开始新的会话
                            mConversationArrayAdapter.clear();

                            break;


                        case BluetoothChatService.STATE_CONNECTING:

                            setStatus(R.string.title_connecting);

                            break;


                        case BluetoothChatService.STATE_LISTEN:

                        case BluetoothChatService.STATE_NONE:

                            setStatus(R.string.title_not_connected);

                            break;

                    }

                    break;

                // 注意以下的 case 的判断全都是基于与其它蓝牙设备已经连接
                // MESSAGE_STATE_CHANGE 的状态为 BluetoothChatService.STATE_CONNECTED
                // 收到蓝牙发送写入的信息(自己发送的信息)
                case  MESSAGE_WRITE:
                    // 得到的 msg.obj 的对象可以直接复用
                    byte[] writeBuf = (byte[]) msg.obj;

                    String writeMessage = new String(writeBuf);

                    mConversationArrayAdapter.add("ME say: " + writeMessage);

                    break;


                // 收到蓝牙收到的信息(其它蓝牙设备发送的信息)
                case MESSAGE_READ:

                    byte[] readBuf = (byte[]) msg.obj;

                    String readMessage = new String(readBuf);

                    mConversationArrayAdapter.add(mConnectedDEviceName +" say: " + readMessage);

                    break;


                // 保存连接到的蓝牙的名字
                case MESSAGE_DEVICE_NAME:

                    mConnectedDEviceName = msg.getData().getString(DEVICE_NAME);

                    Toast.makeText(getApplicationContext(),"连接到 " + mConnectedDEviceName,
                            Toast.LENGTH_SHORT).show();

                    break;

                // 蓝牙的状态改变通知用户
                case MESSAGE_TOAST:

                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_LONG).show();

                    break;

            }
        }
    };

    //  设置标题栏的显示状态
    private final void setStatus(int resId) {

        final ActionBar actionBar = getSupportActionBar();

        actionBar.setSubtitle(resId);

    }

    private final void setStatus(CharSequence subTitle){

        final ActionBar actionBar = getSupportActionBar();

        actionBar.setSubtitle(subTitle);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater =getMenuInflater();

        inflater.inflate(R.menu.option_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Intent serverIntent = null;

        switch (item.getItemId()) {

            case R.id.secure_connect_scan:
                // Launch the DeviceListActivity to see devices and do scan
                serverIntent = new Intent(this, DeviceListActivity.class);

                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);

                return true;

            case R.id.insecure_connect_scan:
                // Launch the DeviceListActivity to see devices and do scan
               // serverIntent = new Intent(this, DeviceListActivity.class);

              //  startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);

               // return true;

            case R.id.discoverable:
                // Ensure this device is discoverable by others
                ensureDiscoverable();

                return true;
        }
        // 返回 true 表示事件已经被处理完 不要向下继续传递
        // 返回 fasle 表示事件没有被处理完 继续传递
        return false;
    }

    //  开启蓝牙设备 300s
    private void ensureDiscoverable() {

        if(D) Log.d(TAG, "ensure discoverable");

        if (mBLuetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {

            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);

            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);

            startActivity(discoverableIntent);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(D) Log.d(TAG, "onActivityResult " + resultCode);

        switch (requestCode) {

            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {

                    connectDevice(data, true);

                }
                break;

//            case REQUEST_CONNECT_DEVICE_INSECURE:
//                // When DeviceListActivity returns with a device to connect
//                if (resultCode == Activity.RESULT_OK) {
//                    connectDevice(data, false);
//                }
//                break;

            case LOCAL_BLUETOOTH_DEVICE_STATE:
                // 用户开启蓝牙后
                if (resultCode == Activity.RESULT_OK) {

                    setupChat();

                } else {
                    // 提示用户没有开启蓝牙
                    Log.d(TAG, "BT not enabled");

                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();

                    finish();
                }
        }
    }

    private void connectDevice(Intent data, boolean secure) {

        // 得到连接设备的 mac 地址
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

        // 得到蓝牙对象
        BluetoothDevice device = mBLuetoothAdapter.getRemoteDevice(address);

        // 连接蓝牙
        mChatService.connect(device, secure);
    }
}