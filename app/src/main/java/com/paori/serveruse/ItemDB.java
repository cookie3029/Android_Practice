package com.paori.serveruse;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

// SQLiteOpenHelper 클래스는 Default Constructor가 없어서
// 생성자를 만들고 매개변수가 있는 생성자를 호출하지 않으면 에러가 발생
public class ItemDB extends SQLiteOpenHelper {
    // 상위 클래스의 생성자를 직접 호출
    // Context는 어떤 정보를 저장한 객체
    // 안드로이드에서는 Context를 매개변수로 하는 메서드가 많은데
    // Context를 대입하라고 하면 Activity 클래스의 인스턴스를 대입하면 됩니다.
    public ItemDB(@Nullable Context context) {
        super(context, "item.db", null, 1);
    }

    // 앱을 설치할 때 호출되는 메서드
    // 여기서는 필요한 테이블을 생성
    @Override
    public void onCreate(SQLiteDatabase db) {
        // 테이블 생성 구문
        db.execSQL("create table item(itemid integer primary key," +
                " itemname, price integer, description, pictureurl, email)");
    }

    // 업그레이드 될 때 호출되는 메서드
    // 여기서는 테이블을 삭제하고 새로 생성하는 코드를 주로 작성
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS item");
        onCreate(db);
    }
}
