package com.paori.serveruse;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    // 최종 업데이트 시간을 저장하기 위한 변수
    private String updateTime;

    // 현재 페이지 번호, 한 페이지 당 데이터 개수, 전체 페이지 개수를 저장할 변수
    private int page;
    private int size;
    private int totalPage;

    // 로컬 데이터베이스 변수
    private ItemDB itemDB;

    // 데이터 목록을 저장할 List
    private List<Item> list;

    // 화면에 보여지는 뷰를 위한 변수
    private ProgressBar downloadProgress;
    private ListView listView;

    // ListView에 데이터를 공급해줄 Adapter
    private ItemAdapter itemAdapter;

    // 가장 하단에서 스크롤 했는지 여부를 저장하기 위한 변수
    private Boolean lastitemVisibleFlag = false;

    // Looper는 메시지 시스템
    // 메인 스레드에게 요청을 전송하는 핸들러
    Handler handler = new Handler(Looper.getMainLooper()){
        // Java에서 메서드 오버라이딩을 할 때 되도록이면 @Override를
        // 습관적으로 붙이는 것이 좋습니다.
        // 메서드의 추상 여부와 추상이 아닌 경우 어떤 작업을 하는지 확인
        @Override
        public void handleMessage(Message message) {
            // 하단이나 상단에 출력되는 메시지 박스가 Snackbar
            /*
                Snackbar.make(MainActivity.this.getWindow().getDecorView(),
                    "데이터 업데이트", Snackbar.LENGTH_LONG).show();
            */

            Toast.makeText(MainActivity.this, "데이터 출력", Toast.LENGTH_LONG).show();

            // ListView에 출력할 데이터 공급자 생성
            // 클래스 안에서 this를 하게 되면 인스턴스 자신
            // anonymous 클래스 안에서 this를 하게 되면 anonymous 클래스의 인스턴스
            // 내부 클래스 안에서 외부 클래스 인스턴스를 사용하고자 할 때는
            // 클래스이름.this를 이용하면 됩니다.
            itemAdapter = new ItemAdapter(MainActivity.this, list, R.layout.item_cell);

            // ListView와 Adapter를 연결
            // 데이터가 변경되면 itemAdapter.notifyDataSetChanged()를 호출
            listView.setAdapter(itemAdapter);

            // 프로그래스 뷰를 화면에서 제거
            downloadProgress.setVisibility(View.GONE);

            // 현재 시간을 파일에 기록
            try {
                // 안드로이드에서 파일에 기록하는 작업은 Data 디렉토리에서만 가능
                // openFIleOutput을 호출하면 Data 디렉토리에 대한 경로 설정을 해줍니다.
                FileOutputStream fos = openFileOutput("updatetime.txt",
                        Context.MODE_PRIVATE);
                fos.write(updateTime.getBytes());
                fos.close();
            } catch (Exception e) {
                Log.e("업데이트 오류", "업데이트 한 시간을 기록하지 못함");
            }
        }
    };

    // 출력하기 위한 데이터를 만드는 스레드
    class DataDisplayThread extends  Thread {
        // 다운로드 받은 문자열을 저장하기 위한 변수
        StringBuilder sb = new StringBuilder();

        // 스레드로 수행할 내용
        @Override
        public void run() {
            try{
                // 업데이트한 시간 가져오기 - GET 방식, 파라미터 없음
                URL url = new URL("http://192.168.55.139/item/updatedate");

                // URL에 연결
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                
                // 옵션 설정
                con.setUseCaches(false);                // 캐싱된 데이터 사용 여부
                con.setConnectTimeout(30000);     // 최대 접속 요청 시간 - 30초

                // 스트림 생성 - 문자열을 다운로드 받기 위한 스트림
                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));

                // 문자열이 아닌 파일 다운로드
                // InputStream is = con.getInputStream();

                // 문자열 읽기
                while(true) {
                    String line = br.readLine();
                    if(line == null) {
                        break;
                    }
                    sb.append(line + "\n");
                }
                br.close();
                con.disconnect();

                // 중간에 다운로드 받은 내용을 출력
                Log.e("다운로드 받은 문자열", sb.toString());

                // 다운로드 받은 문자열이 csv나 XML, Json, Yml이라면 파싱을 해야 하고
                // 아니면 바로 사용 가능

                // JSON 파싱
                // 다운로드 받은 문자열이 {}로 감싸져 있어서 JSONObject로 생성
                // []로 감싸져 있으면 JSONArray로 생성
                JSONObject object = new JSONObject(sb.toString());

                // updatedate 키의 값을 문자열로 가져오기
                String serverUpdateTime = object.getString("updatedate");

                // 서버의 업데이트 시간을 updateTime에 대입
                updateTime = serverUpdateTime;

                // 로컬의 업데이트 타임을 구하기
                String localUpdateTime = null;

                try {
                    FileInputStream fis = openFileInput("updatetime.txt");
                    byte[] data = new byte[fis.available()];
                    fis.read(data);
                    fis.close();

                    localUpdateTime = new String(data);
                } catch (Exception e) {
                    Log.e("업데이트 파일", "업데이트 시간이 기록된 파일이 없음");
                }

                if(serverUpdateTime.equals(localUpdateTime)) {
                    Log.e("업데이트 한 시간 비교", "시간이 같으므로 다운로드 할 필요가 없습니다.");

                    // 전체 페이지 개수를 업데이트 - 전체 데이터가 몇개인지 읽어옵니다.
                    FileInputStream fis = openFileInput("totalpage.txt");
                    byte[] data = new byte[fis.available()];

                    fis.read(data);

                    totalPage = Integer.parseInt(new String(data));

                    fis.close();
                } else {
                    Log.e("업데이트 한 시간 비교", "시간이 다르므로 데이터를 다운로드 해야합니다.");
                    // 데이터를 다운로드 받을 위치 설정
                    url = new URL("http://192.168.55.139/item/list?page=" + page + "&size=" + size);

                    con = (HttpURLConnection) url.openConnection();

                    // 옵션 설정
                    con.setUseCaches(false);                // 캐싱된 데이터 사용 여부
                    con.setConnectTimeout(30000);     // 최대 접속 요청 시간 - 30초

                    br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    sb = new StringBuilder();

                    while(true) {
                        String line = br.readLine();

                        if(line == null) {
                            break;
                        }
                        sb.append(line + "\n");
                    }
                    br.close();
                    con.disconnect();

                    Log.e("다운로드 받은 데이터", sb.toString());

                    // JSON 파싱
                    object = new JSONObject(sb.toString());

                    // 전체 페이지 개수 가져오기
                    totalPage = object.getInt("totalPage");

                    // 전체 페이지 개수를 저장
                    // 서버와 클라이언트의 업데이트한 날짜가 같은 경우 서버의 데이터를 받아오지 않기 때문에
                    // 몇 개의 페이지가 있는지 알지 못하므로
                    // 전체 페이지 개수를 저장해놔야 스크롤의 여부를 결정할 수 있습니다.
                    FileOutputStream fos = openFileOutput("totalpage.txt", Context.MODE_PRIVATE);

                    fos.write(("" + totalPage).getBytes());
                    fos.close();

                    // 아이템 목록 가져오기
                    JSONArray arr = object.getJSONArray("itemList");

                    // 데이터베이스 연결
                    SQLiteDatabase db = itemDB.getWritableDatabase();

                    // ITEM 테이블의 모든 데이터 삭제
                    db.delete("item", null, null);

                    // 순회
                    for(int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);

                        // ContentValues(Map처럼 사용하지만 Entity처럼 동작)
                        // ContentValues를 이용해서 데이터 삽입, 수정, 삭제가 가능

                        ContentValues row = new ContentValues();

                        row.put("itemid", item.getLong("itemid"));
                        row.put("itemname", item.getString("itemname"));
                        row.put("price", item.getInt("price"));
                        row.put("description", item.getString("description"));
                        row.put("pictureurl", item.getString("pictureurl"));
                        row.put("email", item.getString("email"));

                        // item 테이블에 데이터 삽입
                        db.insert("item", null, row);
                    }
                }
                // SQLite 데이터베이스에서 데이터를 읽어서 itemList에 저장

                // 데이터베이스에서 데이터 읽기
                SQLiteDatabase db = itemDB.getReadableDatabase();

                // Cursor는 Iterable, Enumeration과 유사한데
                // next()를 이용해서 다음 데이터를 찾아가는 방식으로 동작하고
                // 읽은 데이터가 없으면 false를 리턴
                Cursor cursor = db.rawQuery(
                        "select itemid, itemname, price, description, pictureurl, email " +
                                "from item order by itemid desc", null
                );

                // 데이터를 저장한 List 클리어
                list.clear();

                // 커서 순회
                while(cursor.moveToNext()) {
                    Item item = new Item();
                    item.setItemid(cursor.getLong(0));
                    item.setItemname(cursor.getString(1));
                    item.setPrice(cursor.getInt(2));
                    item.setDescription(cursor.getString(3));
                    item.setPictureurl(cursor.getString(4));
                    item.setEmail(cursor.getString(5));

                    list.add(item);
                }

                Log.e("list", list.toString());

                // Message 생성
                Message message = new Message();

                // 핸들러에게 메시지 전송
                handler.sendMessage(message);
            } catch (Exception e) {
                Log.e("데이터 다운로드 예외", e.getLocalizedMessage());
            }
        }
    }

    @Override
    // Acitivity가 만들어지면 호출되는 메서드
    // 화면을초기화하고 설정하는 작업을 수행
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 페이지 번호와 한 페이지에 보여질 데이터 개수 초기화
        page = 1;
        size = 15;

        // 데이터베이스 객체 생성
        itemDB = new ItemDB(this);

        // List 초기화
        list = new ArrayList<>();

        // 뷰 찾아오기
        listView = (ListView)findViewById(R.id.listview);
        
        listView.setOnScrollListener(new AbsListView.OnScrollListener(){

            @Override
            // scrollState는 현재 스크롤 상태
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // 현재 스크롤이 멈춰있고 마지막에서 스크롤했다면
                if(scrollState ==
                        AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                        &&
                        lastitemVisibleFlag) {
                    // 다음 페이지에 데이터를 가져오기
                    // page 번호를 1 증가시켜서 다음 페이지의 데이터를 요청
                    if(page >= totalPage) {
                        Toast.makeText(MainActivity.this, "더 이상의  데이터가 없습니다.", Toast.LENGTH_LONG).show();
                    } else {
                        page = page + 1;
                        Log.e("페이지", String.valueOf(page));
                        downloadProgress.setVisibility(View.VISIBLE);

                        new Thread() {
                            // 데이터 다운로드
                            public void run() {
                                try {
                                    // 다운로드 받을 URL을 생성
                                    // 전송 방식은 GET
                                    // 파라미터를 URL 뒤에 ?하고 붙여 넣을 수 있음
                                    // 파라미터는 반드시 UTF-8로 인코딩 되어야 합니다.
                                    // 파라미터에 숫자나 영문자를 제외한 부분이 있으면 인코딩을 해주어야 합니다.
                                    // URLEncoder.encode("인코딩할 문자열", "utf-8")
                                    URL url = new URL(
                                            "http://192.168.55.139/item/list?page="
                                            + page + "&size=" + size);

                                    // 연결 객체 설정
                                    HttpURLConnection con = (HttpURLConnection) url.openConnection();

                                    // 옵션 설정
                                    // 전송 방식 설정
                                    con.setRequestMethod("GET");
                                    con.setConnectTimeout(30000);
                                    con.setUseCaches(false);

                                    // 문자열을 다운로드 받기 위한 스트림을 생성
                                    BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));

                                    // 다운로드 받은 문자열을 저장하기 위한 객체를 생성
                                    StringBuilder sb = new StringBuilder();

                                    // 다운로드 시작
                                    while(true) {
                                        // 한 줄 가져오기
                                        String line = br.readLine();

                                        // 읽어온 데이터가 없다면 중지
                                        if(line == null) {
                                            break;
                                        }

                                        // 읽은 데이터를 StringBuilder에 추가
                                        sb.append(line + "\n");
                                    }

                                    // 연결 객체 정리
                                    br.close();
                                    con.disconnect();

                                    Log.e("다운로드 받은 문자열", sb.toString());

                                    // 다운로드 받은 데이터를 파싱
                                    if(sb.toString().trim().length() > 0) {
                                        // 문자열 전체를 객체로 변환
                                        JSONObject object = new JSONObject(sb.toString());

                                        // error 값 가져오기
                                        String error = object.getString("error");

                                        if(error.equals(null)){
                                            JSONArray arr = object.getJSONArray("itemList");

                                            // 데이터베이스에 대한 참조
                                            SQLiteDatabase db = itemDB.getWritableDatabase();

                                            // 배열 순회
                                            for(int i = 0; i < arr.length(); i++) {
                                                JSONObject obj = arr.getJSONObject(i);

                                                // 파싱한 데이터를 로컬 데이터베이스 저장
                                                ContentValues row = new ContentValues();
                                                Item item = new Item();

                                                Log.e("aaaaaaaa", obj.getString("itemname"));

                                                row.put("itemid", obj.getLong("itemid"));
                                                item.setItemid(obj.getLong("itemid"));

                                                row.put("itemname", obj.getString("itemname"));
                                                item.setItemname(obj.getString("itemname"));

                                                row.put("price", obj.getInt("price"));
                                                item.setPrice(obj.getInt("price"));

                                                row.put("description", obj.getString("description"));
                                                item.setDescription(obj.getString("description"));

                                                row.put("pictureurl", obj.getString("pictureurl"));
                                                item.setPictureurl(obj.getString("pictureurl"));

                                                row.put("email", obj.getString("email"));
                                                item.setEmail(obj.getString("email"));

                                                db.insert("item", null, row);
                                                list.add(item);
                                            }
                                        }
                                    }

                                    // 다시 출력해달라고 요청
                                    // 데이터베이스에서 데이터를 다시 읽어서 재출력해도 되고
                                    // 현재 list에 새로 추가된 데이터만 추가해도 됩니다.
                                    handler.sendEmptyMessage(0);

                                } catch (Exception e) {
                                    Log.e("데이터 가져오기 예외", e.getLocalizedMessage());
                                }
                            }
                        }.start();
                    }
                }
            }

            @Override
            // firstVisibleItem는 가장 위에 보여지는 데이터의 인덱스
            // visibleItemCount는 한 페이지에 보여지는 데이터 개수
            // totalItemCount는 출력되어야 하는 전체 데이터 개수
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // 마지막 부분에서 스크롤을 했는지 여부를 설정
                lastitemVisibleFlag =
                        visibleItemCount > 0
                        &&
                        firstVisibleItem + visibleItemCount >= totalItemCount;
            }
        });
        
        downloadProgress = (ProgressBar) findViewById(R.id.downloadview);

        // 기본 출력을 위한 Adapter 생성과 설정
        itemAdapter = new ItemAdapter(this, list, R.layout.item_cell);
        listView.setAdapter(itemAdapter);

        // 스레드를 만들어서 실행
        new DataDisplayThread().start();

        // 뷰 찾아오기
        SwipeRefreshLayout swipeRefreshLayout =
                (SwipeRefreshLayout) findViewById(R.id.swipe_layout);

        // 스와이프 리프레시 레이아웃에서 아래로 드래그 했을 때 수행되는 이벤트 처리
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                downloadProgress.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                page = 1;
                new DataDisplayThread().start();
            }
        });
    }
}
