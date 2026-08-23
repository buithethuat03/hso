package core;

import event.Event_1;
import event.NauKeo;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Calendar;
import client.Clan;
import client.Player;
import io.Session;
import map.Map;
import map.Mob_in_map;

public class ServerManager implements Runnable {

    private static ServerManager instance;
    private final Thread mythread;
    private Thread server_live;
    private boolean running;
    private ServerSocket server;
    private final long time;

    public ServerManager() {
        this.time = System.currentTimeMillis();
        this.mythread = new Thread(this);
    }

    public static ServerManager gI() {
        if (instance == null) {
            instance = new ServerManager();
        }
        return instance;
    }

    public void init() {
        Manager.gI().init();
        Mob_in_map.create_boss();
        server_update_right_time();
        this.running = true;
        this.mythread.start();
        this.server_live.start();
    }

    public void run() {
        try {
            this.server = new ServerSocket(Manager.gI().server_port);
            System.out.println("Started in " + (System.currentTimeMillis() - this.time) + "s");
            System.out.println();
            System.out.println("LISTEN PORT " + Manager.gI().server_port + "...");
            while (this.running) {
                Socket client = this.server.accept();
//				synchronized (Session.client_entrys) {
                if (this.running) {
                    Session ss = new Session(client);
                    ss.init();
                }
//				}
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void server_update_right_time() {
        this.server_live = new Thread(() -> {
            Calendar now;
            int hour, min, sec, millis;
            SaveData.process();
            while (this.running) {
                try {
                    now = Calendar.getInstance();
                    hour = now.get(Calendar.HOUR_OF_DAY);
                    min = now.get(Calendar.MINUTE);
                    sec = now.get(Calendar.SECOND);
                    millis = now.get(Calendar.MILLISECOND);
                    //
                    if (Mob_in_map.num_boss < 5 && (min == 0) && sec == 3 && (hour % 1 == 0)) {
                        Mob_in_map.create_boss();
                    }
                    if (min % 7 == 0 && sec == 0) {
                        Manager.gI().chatKTGprocess("Chào mừng đến với Hiệp Sĩ Rơm Online - Để xem điểm pk chat : pk. - Chat : thoatket khi bị kẹt map.");
                    }
                    if (min % 2 == 0 && sec == 10) { // update BXH + luu data
                        SaveData.process();
                    }
                    if (hour == 0 && min == 0 && sec == 1) {
                        Manager.gI().ip_create_char.clear();
                        for (Map[] map : Map.entrys) {
                            for (Map map0 : map) {
                                for (int i = 0; i < map0.players.size(); i++) {
                                    map0.players.get(i).change_new_date();
                                }
                            }
                        }
                    }
                    if (Manager.gI().event == 1) {
                        if (Event_1.naukeo == null) {
                            Event_1.naukeo = new NauKeo();
                        }
                        Event_1.naukeo.h = hour;
                        Event_1.naukeo.m = min;
                        if (hour == 17 && min == 00 && sec == 00) {
                            Event_1.list_nhankeo.clear();
                            Event_1.naukeo.start();
                            Manager.gI().chatKTGprocess("@Server: Bắt đầu nấu kẹo, các hiệp sĩ có thể tăng tốc thời gian nấu");
                        }
                        if (sec == 0 && min % 1 == 0) {
                            Event_1.naukeo.update(1);
                        }
                        if (min % 5 == 0 && sec == 0) {
                            Event_1.sort_bxh();
                        }
                    }
                    //
                    long time_sleep = 1000 - millis;
                    if (time_sleep > 0) {
                        if (time_sleep < 100) {
                            System.err.println("server time update process is overloading...");
                        }
                        Thread.sleep(time_sleep);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void close() throws IOException {
        running = false;
        server_live.interrupt();
        server.close();
//        instance = null;
    }
}
