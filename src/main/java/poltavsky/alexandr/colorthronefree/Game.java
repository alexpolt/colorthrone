package poltavsky.alexandr.colorthronefree;


import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.SystemClock;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

public class Game {

    Game( MainActivity.GameView v ) {
        rnd0 = new Random(SystemClock.uptimeMillis());
        view0 = v;
        field = new Block[fieldHeight][fieldWidth];
        statePrev = State.START;
        state = State.PAUSE;
        greatPlayer = false;
        bonusScoreLevel = bonusScoreValue;
        loadSettings();
    }


    //Самое главное происходит здесь
    //Это машина состояний - state machine
    //synchronized так как есть два потока - главный, где отрисовка и реакции на тачи
    //и второй, который делает тики

    synchronized
    void tick() {
        totalTicks++;
        totalTime+=MainActivity.sleep;

        //покажем экран, что это бесплатная версия
        if( totalTicks == 1 || totalScore >= adScoreValue ) {
            adScoreValue=totalScore+adScoreThreshold;
            showBuyAlert();
        }

        switch ( state ) {
            case START:
                //пошел новый блок
                genBlock();
                if( ! block.checkBounds() ) {
                    //Log.e("APP",""+Thread.currentThread().getId()+" clear3 x="+block.x+",y="+block.y);
                    state = State.STOP;
                    break;
                }
                state = State.BLOCK;
                break;

            case STOP:
                //все очищаем
                totalScore = currentScore = 0;
                bonusScoreLevel = bonusScoreValue;
                clearField();
                greatPlayer = false;
                state = State.START;
                break;

            case BLOCK:
                //блок опускается
                if( ++ticks * MainActivity.sleep > timeStep ) {
                    ticks = 0;
                    move(0,1);
                }
                break;

            case FALL:
                //уронили блок
                move(0,1);
                break;

            case CHECK:
                //проверим на совпадения цветов
                check();
                checkScore();
                break;

            case PROCESS:
                //очистим совпавшие цвета
                if( ++ticks * MainActivity.sleep > 100 ) {
                    ticks = 0;
                    processField();
                }
                break;

            case PAUSE:
                return;

            case AD:
                //покажем, что это бесплатная версия и надо купить
                if( adAlertSeconds > 0 && totalTime - endAdAlert > 1000 ){
                    endAdAlert = totalTime;
                    adAlertSeconds--;
                    buttonAd.post(new Runnable() {
                        @Override
                        public void run() {
                            buttonAd.setText( view0.activity.getString( R.string.button_close ) + " " + adAlertSeconds );
                            buttonAd.invalidate();
                        }
                    });
                } else if( adAlertSeconds == 0 ) {
                    buttonAd.post(new Runnable() {
                        @Override
                        public void run() {
                            buttonAd.setText( view0.activity.getString( R.string.button_close ) );
                            buttonAd.setEnabled(true);
                            buttonAd.invalidate();
                        }
                    });
                    adAlertSeconds--;
                }
                return;
        }

        //обновим очки и отрисуем поле
        updateScore();
        updateView();
    }

    void updateView() {
        //не забываем, что отрисовка должна происходить в главном потоке (thread)
        view0.v.post(new Runnable() {
            public void run() { view0.v.invalidate(); }
        } );
    }

    void genBlock() {
        //клонируем блок из предопределенных блоков
        //выбираем рандомно из массива
        //нужна копия, поэтому клонируем
        block = shapes[ rnd0.nextInt(shapes.length) ].cloneShape();
        block.genCoords();
        block.genColors();
    }

    //двигаем блок
    void move(int x, int y) {
        if (state != State.BLOCK && state != State.FALL) return;

        if( ! block.checkBounds(x, y) ) {
            for( Block b : block.blocks )
                field[block.y+b.y][block.x+b.x] = b;
            block = null;
            state = State.CHECK;
            return;
        }

        block.x = block.x + x;
        block.y = block.y + y;
    }

    //проверка поля и маркировка блоков на удаление
    void check() {
        boolean delete = false;

        for( int y = 0; y < field.length; y++ )
            for( int x = 0; x < field[0].length; x++ ) {
                Block b0 = field[y][x];
                Block b1 = x > 0 ? field[y][x-1] : null;
                Block b2 = y > 0 ? field[y-1][x] : null;
                if( b0 != null ) {
                    if( b1 != null && b1.color == b0.color ) {
                        b0.delete = true;
                        b1.delete = true;
                        delete = true;
                    }
                    if( b2 != null && b2.color == b0.color ) {
                        b0.delete = true;
                        b2.delete = true;
                        delete = true;
                    }
                }
            }
        if( ! delete ) {
            state = State.START;
        } else
            state = State.PROCESS;
    }

    //подсчет очков
    void checkScore() {
        int count = 0;
        for (int y = 0; y < field.length; y++)
            for (int x = 0; x < field[0].length; x++) {
                if (field[y][x] != null && field[y][x].delete && ! field[y][x].scored ) {
                    totalScore++;
                    field[y][x].scored = true;
                }
                if (field[y][x] != null && !field[y][x].delete ) count++;
            }

        //Log.e(MainActivity.appName, "count="+count+" score="+totalScore);

        if (!greatPlayer) {
            if( count > 3 * fieldWidth  || totalScore >= bonusScoreLevel ) {
                greatPlayer = true;
                endBonusAlert = totalTicks + 200;
            }
        }

        //бонусная логика
        //count < 3 - бонус в случае менее трех блоков на поле
        if( greatPlayer && count < 3  ) {
            gotBonus = true;

            if( count == 0 && !bonus0 ) {
                bonus0 = true;
                bonus1 = true;
                bonus2 = true;
                bonusScore = 1000;
                totalScore += 1000;
                endGotBonusAlert = totalTicks + 200;
            }
            else if( count == 1 && !bonus1 ) {
                bonus1 = true;
                bonus2 = true;
                bonusScore = 500;
                totalScore += 500;
                endGotBonusAlert = totalTicks + 200;
            }
            else if( count == 2 && !bonus2 ) {
                bonus2 = true;
                bonusScore = 200;
                totalScore += 200;
                endGotBonusAlert = totalTicks + 200;
            }

            adScoreValue=totalScore+adScoreThreshold;
            bonusScoreLevel = totalScore + bonusScoreValue;
        }

        if( gotBonus && count >= 3  ) {
            greatPlayer = false;
            gotBonus = false;
            bonus0 = false;
            bonus1 = false;
            bonus2 = false;
        }
    }

    void updateScore() {
        if( currentScore != totalScore ) currentScore++;
        if( maxScore < currentScore ) maxScore = currentScore;
    }

    //напоминалка с кнопками
    //используем layout_ad из ресурсов
    void showBuyAlert() {
        view0.post(new Runnable() {
            @Override
            public void run() {
                view0.activity.setContentView(R.layout.layout_ad);
                TextView tv0 = (TextView) view0.activity.findViewById(R.id.textView);
                tv0.setText(Html.fromHtml(view0.activity.getString(R.string.text_ad)));
                tv0.setMovementMethod(new ScrollingMovementMethod());
                Button b0 = (Button) view0.activity.findViewById(R.id.button);
                Button b1 = (Button) view0.activity.findViewById(R.id.button2);
                b0.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            view0.activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + MainActivity.appPackageName)));
                        } catch (ActivityNotFoundException anfe) {
                            view0.activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + MainActivity.appPackageName)));
                        }
                    }
                });
                b1.setEnabled(false);
                b1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Game.this.view0.activity.setContentView(Game.this.view0);
                        state = State.PAUSE;
                        Game.this.setResume();
                    }
                });
                buttonAd = b1;
                endAdAlert = totalTime;
                adAlertSeconds = 6;
                Game.this.setAd();
                buttonAd.post(new Runnable() {
                    @Override
                    public void run() {
                        buttonAd.setText(view0.activity.getString(R.string.button_close) + " " + adAlertSeconds);
                        buttonAd.invalidate();
                    }
                });
            }
        });
    }

    //подтираем блоки, помеченные в check
    void processField() {
        for( int y = 0; y < field.length; y++ )
            for( int x = 0; x < field[0].length; x++ )
                if( field[y][x] != null && field[y][x].delete ) {
                    for( int v = y; v >= 0; v-- ) {
                        field[v][x] = v > 0 ? field[v - 1][x] : null;
                    }
                    return;
                }

        state = State.CHECK;
    }

    void clearField() {
        for( int y = 0; y < field.length; y++ )
            for( int x = 0; x < field[0].length; x++ )
                field[y][x] = null;
    }

    //запоминаем максимальные очки
    void saveSettings() {
        SharedPreferences.Editor e = view0.activity.getPreferences(Context.MODE_PRIVATE).edit();
        e.putInt( "maxScore", maxScore );
        e.commit();
    }

    void loadSettings() {
        SharedPreferences p = view0.activity.getPreferences(Context.MODE_PRIVATE);
        maxScore = p.getInt( "maxScore", 0 );
    }


    //отрисовка

    synchronized
    void draw( Canvas c ) {

        float offsetTop = 35 * MainActivity.dpi;
        float w = view0.v.getWidth();
        float h = view0.v.getHeight() - offsetTop;
        float blockWidth = w / fieldWidth, blockHeight = h / fieldHeight;
        Paint p = new Paint();

        //рисуем поле
        for( int y = 0; y < field.length; y++ ) {
            for( int x = 0; x < field[0].length; x++ ) {
                if( field[y][x] != null ) {
                    float rectX = (float) x / fieldWidth * w + 1;
                    float rectY = (float) y / fieldHeight * h + 1 + offsetTop;
                    p.setColor( field[y][x].color );
                    c.drawRect( rectX, rectY, rectX + blockWidth - 2, rectY + blockHeight - 2, p );
                }
            }
        }

        //рисуем падающую фигуру
        if( block != null )
            for( Block b : block.blocks ) {
                float rectX = (float) (block.x+b.x) / fieldWidth * w + 1;
                float rectY = (float) (block.y+b.y) / fieldHeight * h + 1 + offsetTop;
                p.setColor( b.color );
                c.drawRect( rectX, rectY, rectX + blockWidth - 2, rectY + blockHeight - 2, p );
            }

        //рисуем текст
        float marginLeft = 5 * MainActivity.dpi;
        float textSize = 22 * MainActivity.dpi;
        float marginTop = textSize + marginLeft;
        float bonusY = 3*marginTop;

        p.setTextSize( textSize );
        p.setFakeBoldText( true );
        p.setColor( Color.LTGRAY );
        c.drawText( String.format(Locale.US, "%d %s", currentScore, greatPlayer ? view0.activity.getString( R.string.bonus0 ) : ""), marginLeft, marginTop, p );
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText( String.format(Locale.US, "%d",maxScore), w-marginLeft, marginTop, p );

        if (totalTicks < endBonusAlert) {
            p.setTextAlign( Paint.Align.CENTER );
            p.setTextSize( textSize );
            c.drawText( view0.activity.getString( R.string.bonus2 ), w/2,bonusY +  + 1.5f*textSize, p );
            p.setTextSize( textSize*1.2f );
            p.setColor( Color.YELLOW );
            c.drawText( view0.activity.getString( R.string.bonus1 ), w/2, bonusY, p );
        }
        if (totalTicks < endGotBonusAlert) {
            p.setTextAlign( Paint.Align.CENTER );
            p.setTextSize( textSize*2 );
            p.setColor( Color.YELLOW );
            c.drawText( "+"+bonusScore+"!", w/2, bonusY, p );
        }
    }

    //реакция на действия пользователя
    synchronized void moveUser(int x, int y) { if (state == State.BLOCK && block.checkBounds(x,y) ) move( x, y ); updateView(); }
    synchronized void dropUser() { if( state == State.BLOCK ) state = State.FALL; }
    synchronized void rotateUser(){ if (state == State.BLOCK ) block.rotate(); updateView(); }
    synchronized void clickLeftUser(){ if (state == State.BLOCK ) block.shiftColorsLeft(true); updateView(); }
    synchronized void clickRightUser(){ if (state == State.BLOCK ) block.shiftColorsRight(true); updateView(); }
    synchronized void setPause() {
        if( state == State.PAUSE ) {
            Log.wtf(MainActivity.appName, "State is already paused!");
            return;
        }
        if( state == State.AD ) return;
        statePrev = state; state = State.PAUSE;
    }
    synchronized void setResume() {
        if( state == State.PAUSE )
            state = statePrev;
    }
    synchronized void setAd() {
        setPause();
        state = State.AD;
    }

    //немного продублировано получилось
    //вызываются методы выше
    void moveLeft() { moveUser(-1,0); }
    void moveRight() { moveUser(1,0); }
    void moveUp() { rotateUser(); }
    void moveDown() { dropUser(); }
    void clickLeft() { clickLeftUser(); }
    void clickRight() { clickRightUser(); }

    //Класс блока
    //содержит координаты (локальные в BlockGroup)  и цвет, состояние
    class Block {
        Block() {
            x = field[0].length/2; y = 0;
            color = colors[ rnd0.nextInt(colors.length) ];
        }
        Block( int x_, int y_, int color_ ) {
            x = x_;
            y = y_;
            color = color_;
        }
        Block( int x_, int y_ ) {
            x = x_;
            y = y_;
            color = Color.WHITE;
        }
        Block cloneBlock() {
            return new Block(x,y,color);
        }
        int x, y, color;
        boolean delete, scored;
    }


    //Класс фигуры - несколько блоков вместе
    //содержит координаты центра всей фигуры
    //отвечает за вращение фигуры и цветов
    //а также проверяет на допустимусть перемещения и вращения
    class BlockGroup {
        BlockGroup( boolean rotation_, Block... blocks_ ) {
            rotation = rotation_;
            blocks = blocks_;
        }
        BlockGroup( int x_, int y_, boolean rotation_, Block[] blocks_ ) {
            rotation = rotation_;
            x = x_; y = y_;
            blocks = new Block[ blocks_.length ];
            for(int i=0; i<blocks_.length;i++ ) blocks[i] = blocks_[i].cloneBlock();
        }
        boolean checkBounds() {
            return checkBounds(0,0);
        }
        boolean checkBounds(int moveX, int moveY) {
            for( Block b: blocks ) {
                int realX = b.x + x + moveX;
                int realY = b.y + y + moveY;
                if( realX < 0 || realX == fieldWidth ) {
                    return false;
                }
                if( realY < 0 || realY == fieldHeight ) {
                    return false;
                }
                if( field[realY][realX] != null ) {
                    return false;
                }
            }
            return true;
        }
        class Rot {
            int moveX, moveY;
            boolean notok, ok;
        }
        void rotate() {
            if(rotation) {
                Rot p1 = checkRotate();
                if( p1.notok ) return;
                if( ! p1.ok ) {
                    Rot p2 = checkRotate( p1.moveX, p1.moveY );
                    if( p2.notok ) return;
                    move( p1.moveX, p1.moveY );
                };

                for (Block b : blocks) {
                    int newY = b.x;
                    int newX = -b.y;
                    b.x = newX;
                    b.y = newY;
                    angle++;
                }
            }
        }
        Rot checkRotate() {
            return checkRotate(0,0);
        }
        Rot checkRotate(int x_, int y_) {
            Rot r = new Rot();
            for (Block b : blocks) {
                int newY = y + y_ + b.x, newX = x + x_ - b.y;
                if( newY >= fieldHeight ) { r.notok = true; return r; }
                if( newY < 0 ) { r.moveY = 1; return r; }
                if( newX < 0 ) { r.moveX = 1; return r; }
                if( newX >= fieldWidth ) { r.moveX = -1; return r; }
                if( field[newY][newX] != null ) { r.notok = true; return r; }
            }
            r.ok = true;
            return r;
        }

        void shiftColorsLeft(boolean user) {
            if( user && angle%2 == 0 && angle%4 != 0 ) {
                shiftColorsRight( false );
                return;
            }
            int color0 = blocks[0].color;
            int i; for( i = 0; i < blocks.length-1; i++ ) blocks[i].color = blocks[i+1].color;
            blocks[i].color = color0;
        }
        void shiftColorsRight(boolean user) {
            if( user && angle%2 == 0 && angle%4 != 0 ) {
                shiftColorsLeft( false );
                return;
            }
            int color0 = blocks[blocks.length-1].color;
            int i; for( i = blocks.length-1; i > 0; i-- ) blocks[i].color = blocks[i-1].color;
            blocks[i].color = color0;
        }
        void genCoords() {
            int newX = rnd0.nextInt( fieldWidth );
            int newX_ = newX;
            while( ! checkBounds(newX%fieldWidth,0) && newX < newX_ + fieldWidth ) {
                newX++;
            }
            x = newX % fieldWidth;
            y = 0;
        }
        void genColors() {
            int prevColor = Color.BLACK;
            for( Block b: blocks ) {
                do {
                    b.color = colors[ rnd0.nextInt(colors.length) ];
                } while( b.color == prevColor );
                prevColor = b.color;
            }
        }
        BlockGroup cloneShape() {
            return new BlockGroup(x,y,rotation,blocks);
        }
        Block blocks[];
        int x, y;
        boolean rotation;
        int angle;
    }

    //наша стейт машина
    enum State {
        START, BLOCK, STOP, FALL, PROCESS, CHECK, PAUSE, AD
    }

    final static int fieldWidth = 6;
    final static int fieldHeight = 10;


    volatile BlockGroup block;

    //предопределенные фигуры
    //у каждой есть центр вращения - блок с координатами 0,0
    BlockGroup shapes[] = {
            new BlockGroup( false, new Block(0,0) ),
            new BlockGroup( true, new Block(0,0), new Block(1,0) ),
            new BlockGroup( true, new Block(0,1), new Block(0,0), new Block(1,0)),
            new BlockGroup( true, new Block(-1,0), new Block(0,0), new Block(1,0) ),
            new BlockGroup( false, new Block(0,0), new Block(1,0), new Block(1,1), new Block(0,1)),
    };

    //куча разные переменных
    State state, statePrev;
    long ticks, totalTicks, totalTime;
    int currentScore, totalScore, maxScore, bonusScore, bonusScoreLevel;
    int adScoreValue, adScoreThreshold = 150;
    final int bonusScoreValue = 100;
    final static long timeStep = 1200;

    MainActivity.GameView view0;

    //набор цветов для блоков
    int colors[] = new int[]{ Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA, Color.CYAN };

    //главное поле
    Block field[][];

    //бонусы
    boolean greatPlayer, gotBonus, bonus0, bonus1, bonus2;

    Random rnd0;
    long endBonusAlert, endGotBonusAlert;

    //напоминалка
    long endAdAlert, adAlertSeconds;

    Button buttonAd;
}

