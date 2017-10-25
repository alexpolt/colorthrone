package poltavsky.alexandr.colorthronefree;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    //Стартовый экран
    StartView view0;

    //Поле игры
    GameView view1;

    //Контроллер игры
    Game game0;

    //Логика для паузы при нажатии кнопки "домой"
    boolean running = false;

    //Отдельный поток для тиков игры
    Thread main;

    //1 так = 20 миллисекунд
    static final long sleep = 20;

    //Разные переменные
    static String appName;
    static float dpi;
    static int w,h;
    static String appPackageName = "poltavsky.alexandr.colorthrone";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        appName = getString(R.string.app_name);
    }


    //Кишки начинаются здесь (life cycle of an android app)
    //Можно было начинать в onCreate
    @Override
    protected void onStart() {
        super.onStart();
        Log.wtf(appName, "onStart");

        if(running) {

            game0.setResume();

        } else {

            //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            dpi = dm.density;

            //показываем первый экран
            view0 = new StartView(this);
            //чтобы получить координаты экрана после начала отрисовки
            //надо использовать post метод (вся отрисовка должны происходить
            //в главном потоке, метод post позволяет это сделать)
            view0.post(new Runnable() {
                @Override
                public void run() {
                    w =  view0.getWidth();
                    h =  view0.getHeight();
                    view1 = new GameView(MainActivity.this);
                    game0 = new Game( view1 );
                }
            });

            //показать стартовый экран
            this.setContentView(view0);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.wtf(appName, "onDestroy");
        running = false;
        try { if( running ) main.join(); } catch (Exception e) { Log.e(appName, e.toString()); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.wtf(appName, "onPause");
        if(running) {
            game0.setPause();
        }
    }

    //Главный цикл игры
    //Здесь начинаются тики
    void startLoop() {
        running = true;
        main = new Thread( new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try { Thread.sleep(sleep, 0); } catch (Exception e) { Log.e(appName, e.toString()); }
                    game0.tick();
                }
            }
        });
        main.start();
    }

    //Стартовый экран
    class StartView extends LinearLayout {

        MainActivity activity;

        StartView(MainActivity c ) {
            super(c);
            activity = c;

            //Настраиваем, добавляем кнопки
            //можно было использовать Layout Designer
            //но руками быстрее

            this.setOrientation( LinearLayout.VERTICAL );
            this.setGravity( Gravity.CENTER_HORIZONTAL );

            int size = (int)(dpi * 144);

            ImageView im0 = new ImageView(c);
            im0.setImageResource( R.drawable.ic_launcher );
            LinearLayout.LayoutParams imLp = new LinearLayout.LayoutParams(size,size);
            imLp.setMargins(0,(int)(dpi*50),0,(int)(dpi*30));
            im0.setLayoutParams( imLp );
            im0.setScaleType(ImageView.ScaleType.FIT_XY);
            im0.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    //показываем поле игры
                    activity.setContentView( view1 );
                    //стартуем цикл игры при нажатии на лого
                    startLoop();
                    game0.setResume();
                }
            });
            this.addView( im0 );

            TextView tv0 = new TextView(c);
            tv0.setText( Html.fromHtml( activity.getString(R.string.welcome)) );
            tv0.setMovementMethod( new ScrollingMovementMethod() );
            tv0.setGravity(Gravity.CENTER);
            tv0.setLayoutParams( new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
            this.addView( tv0 );}

    }

    //Поле игры
    class GameView extends LinearLayout  {
        int slop, slopDown, slopMove, slopUp;
        float x, y;
        boolean moving, down;
        View v;
        MainActivity activity;

        GameView(MainActivity c) {
            super( c );
            activity = c;

            setOrientation( LinearLayout.VERTICAL );
            setGravity( Gravity.CENTER_HORIZONTAL );
            setBackgroundColor( Color.rgb(20,20,20) );

            v = new GameCanvas(c);
            v.setBackgroundColor( Color.rgb(20,20,20) );
            slop = ViewConfiguration.get(c).getScaledEdgeSlop();
            //отскалированые под плотность точек на экране дистанции для различения жестов
            slopMove = 4 * slop;
            slopDown = 4 * slop;
            slopUp = 4 * slop;

/*
пока что не нужно
логика для отоброжения на любом экране с заданным aspect ratio

            float ar0 = (float) w / h, ar1 = (float) Game.fieldWidth / Game.fieldHeight;
            if( ar0 <= ar1 ) {
                int width = w, height = (int) (w  / ar1);
                v.setLayoutParams( new ViewGroup.LayoutParams( width, height ));
            } else {
                int width = (int) (h * ar1), height = h;
                v.setLayoutParams( new ViewGroup.LayoutParams( width, height ));
            }
*/

            this.addView( v );
        }

        //канвас, на котором все рисуется и который реагирует на прикосновения
        class GameCanvas extends View {
            long startTap;

            GameCanvas( Context c ) {
                super( c );
            }
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                float dx, dy;
                int dist;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x = e.getX(); y = e.getY();
                        moving = false;
                        down = false;
                        startTap = SystemClock.uptimeMillis();
                        break;
                    case MotionEvent.ACTION_UP:
                        //Log.e(appName, "x="+x+", dx="+dx+", y="+y+", dy="+dy);
                        if(!moving) {
                            dx = e.getX() - x;
                            dy = e.getY() - y;
                            dist = (int)Math.sqrt(dx*dx+dy*dy);
                            if( dist > slopUp && dy < 0)  game0.moveUp();
                            else if ( SystemClock.uptimeMillis() - startTap < 500 ) {
                                if (e.getX() < getWidth() / 2) {
                                    game0.clickLeft();
                                } else {
                                    game0.clickRight();
                                }
                            }
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        dx = e.getX() - x;
                        dy = e.getY() - y;
                        dist = (int)Math.sqrt(dx*dx+dy*dy);
                        if(dist>slopMove||dist>slopDown) {
                            if(Math.abs(dx) > Math.abs(dy)) {
                                if (dx < 0) game0.moveLeft();
                                else game0.moveRight();
                                x = e.getX();
                                y = e.getY();
                                moving = true;
                            }
                            else if( dy > 0 && dist > slopDown && ! down ) {
                                game0.moveDown();
                                down = true;
                            }
                        }
                        break;
                }
                return true;
            }
            public void onDraw(Canvas c) {
                super.onDraw( c );
                game0.draw( c );
            }
        }
    }


}



