package com.example.ncku.hospitalnavigation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.ncku.hospitalnavigation.utils.StringUtil;
import com.example.ncku.hospitalnavigation.utils.Utils;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

@SuppressLint("NewApi")
public class MainActivity extends Activity {
    private final static String TAG = "MainActivity";
    private final static int MAIN_ENTRANCE = 1; //大門
    private final static int FLUID_LABORATORY = 2; //計算流體實驗室
    private final static int CLASS_41122 = 3; //計算流體實驗室
    private SensorManager mSensorManager;
    private Sensor mDirectionSensor;
    private Sensor mOrientationSensor;
    private Sensor mAccSensor;
    // Router Planning Map
    private ImageView mMapImage;
    // 目前位置的 X/Y 座標
    private ImageView mArrowIcon;
    private float mCoordinateX;
    private float mCoordinateY;
    // ScrollView:上下 / HorizontalScrollView:左右
    private ScrollView mScrollView;
    private float mScrollViewY;
    private float mScrollViewRealY;
    private HorizontalScrollView mHorizontalScrollView;
    private float mScrollViewX;
    private float mScrollViewRealX;
    // 水平偵測
    private boolean mMoveReady;
    // 目前方位的角度
    private float mCurrentDirectionAngle;
    // API
    private String mToken;
    private String mMapUrl;
    private int mMyStartPoint = 0;
    private int mMyEndPoint = 2;
    // 行走的步伐大小(步距)，需從圖資計算此值 -> 圖資寬 / 總步數 = 一步的步距
    private final float mStepDistanceX = 30.25f;
    private final float mStepDistanceY = 42.5f;
    // AlertDialog UI
    private AlertDialog mLocationDialog;
    private EditText mMyLocationET;
    private String mMyLocationName;
    private Spinner mDestinationSpinner;
    private TextView mTitleLocation;
    private TextView mTitleDestination;
    // ProgressDialog UI
    private ProgressDialog mWaitProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // 方向感應器
        mDirectionSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        mSensorManager.registerListener(mDirectionSensorEvent, mDirectionSensor, SensorManager.SENSOR_DELAY_GAME);
        // 水平感應器
        mOrientationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        mSensorManager.registerListener(mOrientationSensorEvent, mOrientationSensor, SensorManager.SENSOR_DELAY_GAME);
        // 加速度感應器
        mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(mAccSensorEvent, mAccSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(mDirectionSensorEvent);
        mSensorManager.unregisterListener(mOrientationSensorEvent);
        mSensorManager.unregisterListener(mAccSensorEvent);
    }

    private void initView() {
        mTitleLocation = (TextView) findViewById(R.id.location_textView);
        mTitleDestination = (TextView) findViewById(R.id.destination_textView);
        mArrowIcon = (ImageView) findViewById(R.id.arrow_icon);
        mMapImage = (ImageView) findViewById(R.id.map_pic);
        mMapImage.setImageBitmap(Utils.getBitmap(this, R.drawable.school_map));
        // 圖資上下滑動時的動作
        mScrollView = (ScrollView) findViewById(R.id.scrollView);
        mScrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                int scrollY = mScrollView.getScrollY();
                mArrowIcon.setY(mCoordinateY - scrollY);
                mScrollViewY = scrollY;
            }
        });
        // 圖資左右滑動時的動作
        mHorizontalScrollView = (HorizontalScrollView) findViewById(R.id.horizontalScrollView);
        mHorizontalScrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                int scrollX = mHorizontalScrollView.getScrollX();
                mArrowIcon.setX(mCoordinateX - scrollX);
                mScrollViewX = scrollX;
            }
        });
        // 彈跳出輸入目前位置和目的地的 AlertDialog
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                locationSelectDialog();
            }
        });
    }

    // 選擇所在地/目的地的 AlertDialog
    private void locationSelectDialog() {
        final View item = LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_select_position, null);
        // 選擇目的地
        mDestinationSpinner = (Spinner) item.findViewById(R.id.destination_spinner);
        ArrayAdapter<CharSequence> spinnerAdapter = new ArrayAdapter<CharSequence>(
                this, R.layout.spinner_text, StringUtil.getSpinnerItems(this, R.array.classes));
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDestinationSpinner.setAdapter(spinnerAdapter);

        mDestinationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String mClassName = ((TextView) mDestinationSpinner.getSelectedView()).getText().toString();
                mTitleDestination.setText(mClassName);
                mMyEndPoint = Integer.valueOf(StringUtil.getSpinnerValue(MainActivity.this, R.array.classes, mClassName));
                if (mMyStartPoint == 0 || mMyStartPoint == mMyEndPoint) {
                    mLocationDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                } else {
                    mLocationDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        mDestinationSpinner.setSelection(mMyEndPoint - 1);

        // 掃描所在地的QR Code
        mMyLocationET = (EditText) item.findViewById(R.id.my_location_editText);
        if ((mMyLocationName != null) && !mMyLocationName.equals("")) {
            mMyLocationET.setText(mMyLocationName);
        }
        mMyLocationET.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IntentIntegrator scanIntegrator = new IntentIntegrator(MainActivity.this);
                scanIntegrator.initiateScan();
            }
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(item)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if ((mToken == null) || mToken.equals("")) {
                            getToken();
                        } else {
                            getNaviMap(mToken, mMyStartPoint, mMyEndPoint);
                        }
                        showProgressDialog();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
        mLocationDialog = builder.create();
        mLocationDialog.show();
        mLocationDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    private void initData() {
        new Timer(true).schedule(new TimerTask() {
            @Override
            public void run() {
                // 取得箭頭目前的 X / Y 坐標 ; 預設為置中
                mCoordinateX = mArrowIcon.getX();
                mCoordinateY = mArrowIcon.getY();
            }
        }, 500);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // QR Code Scanner Callback
        IntentResult scanningResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanningResult != null) {
            String scanContent = scanningResult.getContents();
            if (scanContent != null) {
                mTitleLocation.setText("" + scanContent.toString());
                mMyLocationET.setText("" + scanContent.toString());
                mMyLocationName = "" + scanContent.toString();
                mMyStartPoint = Integer.valueOf(StringUtil.getSpinnerValue(MainActivity.this, R.array.classes, scanContent.toString()));
                // 判斷所在地和目的地是否一樣
                if (mMyStartPoint == 0 || mMyStartPoint == mMyEndPoint) {
                    mLocationDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                } else {
                    mLocationDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            }
        } else {
            Toast.makeText(getApplicationContext(), "nothing", Toast.LENGTH_SHORT).show();
        }
    }

    // Zero-crossing 步數演算法
    // 當 gravity 值為(正 -> 負 -> 正)時算一步
    private final float mThreshold = 0.65f; // 此值可自行微調(值愈小愈敏感)，當重力大於此值時為Peak反之為valley
    private final float mStepInterval = 500; // 0.4秒 : 每步的間隔時間
    private boolean isPeak = false;
    private boolean isValley = false;
    private long mPreviousTimeStamp = 0L;
    private long mNowTimeStamp = 0L;
    /**
     * Acc Sensor Event Listener
     */
    private SensorEventListener mAccSensorEvent = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mNowTimeStamp = System.currentTimeMillis();
            float axisZ = event.values[2];
            float gravity = (axisZ - SensorManager.GRAVITY_EARTH);
            if (gravity > mThreshold && !isPeak) {
                isPeak = true;
            }
            if (!isValley && isPeak) {
                isValley = gravity < -mThreshold ? true : false;
            }
            if (isValley) {
                if (gravity > 0) {
                    if (mNowTimeStamp - mPreviousTimeStamp > mStepInterval) {
                        if (mMoveReady) {
                            moveArrow();
                        }
                    }
                    isPeak = false;
                    isValley = false;
                    mPreviousTimeStamp = mNowTimeStamp;
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    /**
     * 1.移動目前位置(arrow icon)
     * 2.X/Y的座標移動量需用 Sin -> Y 和 cos -> X 來算
     */
    private void moveArrow() {
        if (mCurrentDirectionAngle == 0) {
            mCurrentDirectionAngle = 360;
        }
        // 北(1) ~ 正東方位(90)
        if (mCurrentDirectionAngle - 90 <= 0) {
            mCurrentDirectionAngle = 90 - mCurrentDirectionAngle;
            double ratioX = Math.cos(mCurrentDirectionAngle * Math.PI / 180);
            double ratioY = Math.sin(mCurrentDirectionAngle * Math.PI / 180);
            mCoordinateX -= mScrollViewX;
            mCoordinateX += mStepDistanceX * ratioX;
            mArrowIcon.setX(mCoordinateX);
            mScrollViewRealX = (float) (mScrollViewRealX + mStepDistanceX * ratioX);
            mHorizontalScrollView.smoothScrollTo((int) mScrollViewRealX, 0);
            mCoordinateX += mScrollViewX;

            mCoordinateY -= mScrollViewY;
            mCoordinateY -= mStepDistanceY * ratioY;
            mArrowIcon.setY(mCoordinateY);
            mScrollViewRealY = (float) (mScrollViewRealY - mStepDistanceY * ratioY);
            mScrollView.smoothScrollTo(0, (int) mScrollViewRealY);
            mCoordinateY += mScrollViewY;
        } else if (mCurrentDirectionAngle - 180 <= 0) { // 東(91) ~ 正南方位(180)
            mCurrentDirectionAngle = mCurrentDirectionAngle - 91;
            double ratioX = Math.cos(mCurrentDirectionAngle * Math.PI / 180);
            double ratioY = Math.sin(mCurrentDirectionAngle * Math.PI / 180);
            mCoordinateX -= mScrollViewX;
            mCoordinateX += mStepDistanceX * ratioX;
            mArrowIcon.setX(mCoordinateX);
            mScrollViewRealX = (float) (mScrollViewRealX + mStepDistanceX * ratioX);
            mHorizontalScrollView.smoothScrollTo((int) mScrollViewRealX, 0);
            mCoordinateX += mScrollViewX;

            mCoordinateY -= mScrollViewY;
            mCoordinateY += mStepDistanceY * ratioY;
            mArrowIcon.setY(mCoordinateY);
            mScrollViewRealY = (float) (mScrollViewRealY + mStepDistanceY * ratioY);
            mScrollView.smoothScrollTo(0, (int) mScrollViewRealY);
            mCoordinateY += mScrollViewY;
        } else if (mCurrentDirectionAngle - 270 <= 0) { // 南(181) ~ 正西方位(270)
            mCurrentDirectionAngle = 270 - mCurrentDirectionAngle;
            double ratioX = Math.cos(mCurrentDirectionAngle * Math.PI / 180);
            double ratioY = Math.sin(mCurrentDirectionAngle * Math.PI / 180);
            mCoordinateX -= mScrollViewX;
            mCoordinateX -= mStepDistanceX * ratioX;
            mArrowIcon.setX(mCoordinateX);
            mScrollViewRealX = (float) (mScrollViewRealX - mStepDistanceX * ratioX);
            mHorizontalScrollView.smoothScrollTo((int) mScrollViewRealX, 0);
            mCoordinateX += mScrollViewX;

            mCoordinateY -= mScrollViewY;
            mCoordinateY += mStepDistanceY * ratioY;
            mArrowIcon.setY(mCoordinateY);
            mScrollViewRealY = (float) (mScrollViewRealY + mStepDistanceY * ratioY);
            mScrollView.smoothScrollTo(0, (int) mScrollViewRealY);
            mCoordinateY += mScrollViewY;
        } else { // 西(271) ~ 北方位(360)
            mCurrentDirectionAngle = mCurrentDirectionAngle - 271;
            double ratioX = Math.cos(mCurrentDirectionAngle * Math.PI / 180);
            double ratioY = Math.sin(mCurrentDirectionAngle * Math.PI / 180);
            mCoordinateX -= mScrollViewX;
            mCoordinateX -= mStepDistanceX * ratioX;
            mArrowIcon.setX(mCoordinateX);
            mScrollViewRealX = (float) (mScrollViewRealX - mStepDistanceX * ratioX);
            mHorizontalScrollView.smoothScrollTo((int) mScrollViewRealX, 0);
            mCoordinateX += mScrollViewX;

            mCoordinateY -= mScrollViewY;
            mCoordinateY -= mStepDistanceY * ratioY;
            mArrowIcon.setY(mCoordinateY);
            mScrollViewRealY = (float) (mScrollViewRealY - mStepDistanceY * ratioY);
            mScrollView.smoothScrollTo(0, (int) mScrollViewRealY);
            mCoordinateY += mScrollViewY;
        }
    }

    /**
     * Direction Sensor Event Listener
     */
    private SensorEventListener mDirectionSensorEvent = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            // get the angle around the z-axis rotated
            float angle = Math.round(event.values[0]);
            // 工科館不是正北有偏個幾度，所以要進行角度校正來配合圖資顯示
            angle += 5;
            if (angle > 360) {
                angle -= 360;
            }
            mArrowIcon.setRotation(angle);
            mCurrentDirectionAngle = angle;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    /**
     * Orientation Sensor Event Listener
     */
    private SensorEventListener mOrientationSensorEvent = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mMoveReady = Math.abs(event.values[SensorManager.DATA_Y]) < 45 &&
                    Math.abs(event.values[SensorManager.DATA_Z]) < 30;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    // 取得API要使用的Token
    private void getToken() {
        String url = "https://hospital.rickhsu.me/api/v1/authenticate";
        RequestQueue mRequestQueue = Volley.newRequestQueue(this);
        StringRequest mStringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jObject = new JSONObject(response);
                            mToken = jObject.getString("token");
                            getNaviMap(mToken, mMyStartPoint, mMyEndPoint);
                        } catch (JSONException e) {
                            Log.e(TAG, "" + e.getMessage());
                        }
                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error getToken :" + error.getMessage());
                dismissProcessDialog();
                Toast.makeText(MainActivity.this, "" + error.getMessage(), Toast.LENGTH_SHORT).show();
            }

        }) {
            @Override
            protected HashMap<String, String> getParams() throws AuthFailureError {
                HashMap<String, String> hashMap = new HashMap<String, String>();
                hashMap.put("email", "demo@hospital.me");
                hashMap.put("password", "123456789");
                return hashMap;
            }
        };
        mRequestQueue.add(mStringRequest);
    }

    // 取得所有地點的清單
    private void getLocationList(final String token) {
        String url = "https://hospital.rickhsu.me/api/v1/get-all-locations";
        RequestQueue mRequestQueue = Volley.newRequestQueue(this);
        StringRequest mStringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, "" + response.toString());
                    }

                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error:getLocationList " + error.getMessage());
            }

        }) {

            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Authorization", "Bearer [" + token + "]");
                return headers;
            }
        };
        mRequestQueue.add(mStringRequest);
    }

    /**
     * 取得路線規畫圖資的URL
     */
    private void getNaviMap(final String token, int startPoint, int endPoint) {
        String url = "https://hospital.rickhsu.me/api/v1/get-mapy-by-start-point-and-end-point/"
                + startPoint + "/" + endPoint;
        RequestQueue mRequestQueue = Volley.newRequestQueue(this);
        StringRequest mStringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jObject = new JSONObject(response);
                            String mapRetrivev = jObject.getString("mapRetrieve");
                            JSONArray array = new JSONArray(mapRetrivev);
                            mMapUrl = array.getJSONObject(0).getString("image_path");
                            getMapBitmap(mMapUrl);
                        } catch (JSONException e) {
                            Log.e(TAG, "" + e.getMessage());
                        }
                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error:getNaviMap " + error.getMessage());
                dismissProcessDialog();
                Toast.makeText(MainActivity.this, "" + error.getMessage(), Toast.LENGTH_SHORT).show();
            }

        }) {

            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Authorization", "Bearer [" + token + "]");
                return headers;
            }
        };
        mRequestQueue.add(mStringRequest);
    }

    // 下載圖資
    private void getMapBitmap(String mapUrl) {
        RequestQueue mRequestQueue = Volley.newRequestQueue(this);
        ImageRequest imageRequest = new ImageRequest(mapUrl,
                new Response.Listener<Bitmap>() {
                    @Override
                    public void onResponse(Bitmap response) {
                        mMapImage.setImageBitmap(response);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                setPositionCoordinate(mMyStartPoint);
                                dismissProcessDialog();
                                (findViewById(R.id.compass_icon)).setVisibility(View.VISIBLE);
                            }
                        }, 500);
                    }
                }, 0, 0, Bitmap.Config.RGB_565, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error:getMapBitmap " + error.getMessage());
                dismissProcessDialog();
                Toast.makeText(MainActivity.this, "" + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        mRequestQueue.add(imageRequest);
    }

    /**
     * 設定所在地的 X/Y 坐標
     *
     * @param id
     */
    public void setPositionCoordinate(int id) {
        switch (id) {
            case MAIN_ENTRANCE:
                final int coordinateDoorX = 4100 - (mArrowIcon.getWidth() / 2);
                final int coordinateDoorY = 1190 - (mArrowIcon.getHeight() / 2);
                mHorizontalScrollView.smoothScrollTo(3352, 0);
                mScrollViewX = 3352;
                mScrollViewRealX = 3352;
                mScrollView.smoothScrollTo(0, 352);
                mScrollViewY = 352;
                mScrollViewRealY = 352;
                if (mCoordinateX == coordinateDoorX && mCoordinateY == coordinateDoorY) {
                    break;
                }
                mArrowIcon.setX(coordinateDoorX);
                mCoordinateX = coordinateDoorX;
                mArrowIcon.setY(coordinateDoorY);
                mCoordinateY = coordinateDoorY;
                break;
            case FLUID_LABORATORY:
                final int CoordinateComputerX = 3607 - (mArrowIcon.getWidth() / 2);
                final int CoordinateComputerY = 385 - (mArrowIcon.getHeight() / 2);
                mHorizontalScrollView.smoothScrollTo(3066, 0);
                mScrollViewX = 3066;
                mScrollViewRealX = 3066;
                mScrollView.smoothScrollTo(0, 0);
                mScrollViewY = 0;
                mScrollViewRealY = 0;
                if (mCoordinateX == CoordinateComputerX && mCoordinateY == CoordinateComputerY) {
                    break;
                }
                mArrowIcon.setX(CoordinateComputerX);
                mCoordinateX = CoordinateComputerX;
                mArrowIcon.setY(CoordinateComputerY);
                mCoordinateY = CoordinateComputerY;
                break;
            case CLASS_41122:
                final int Coordinate41122X = 3607 - (mArrowIcon.getWidth() / 2);
                final int coordinate41122Y = 2015 - (mArrowIcon.getHeight() / 2);
                mHorizontalScrollView.smoothScrollTo(3066, 0);
                mScrollViewX = 3066;
                mScrollViewRealX = 3066;
                mScrollView.smoothScrollTo(0, 699);
                mScrollViewY = 699;
                mScrollViewRealY = 699;
                if (mCoordinateX == Coordinate41122X && mCoordinateY == coordinate41122Y) {
                    break;
                }
                mArrowIcon.setX(Coordinate41122X);
                mCoordinateX = Coordinate41122X;
                mArrowIcon.setY(coordinate41122Y);
                mCoordinateY = coordinate41122Y;
                break;
        }
    }

    // 顯示 ProgressDialog
    private void showProgressDialog() {
        if (mWaitProgressDialog == null) {
            mWaitProgressDialog = new ProgressDialog(MainActivity.this);
        }
        if (!mWaitProgressDialog.isShowing()) {
            mWaitProgressDialog = ProgressDialog.show(MainActivity.this, "取得圖資中", "請稍候...", true);
            mWaitProgressDialog.setCancelable(true);
        }
    }

    // 隱藏 ProgressDialog
    private void dismissProcessDialog() {
        if (mWaitProgressDialog.isShowing()) {
            mWaitProgressDialog.dismiss();
        }
    }
}

