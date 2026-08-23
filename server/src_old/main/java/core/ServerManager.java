package core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Calendar;
import client.Clan;
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
        BXH.init();
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
                    if (min % 5 == 0 && sec == 10) { // update BXH + luu data
                        try {
                            Manager.gI().chatKTGprocess(
                                    "@Server: Server bắt đầu lưu data định kỳ, có thể sẽ gây lag trong vài phút");
                            Clan.flush();
                            BXH.init();
                            for (Map[] map : Map.entrys) {
                                for (Map map0 : map) {
                                    for (int i = 0; i < map0.players.size(); i++) {
                                        map0.players.get(i).flush();
                                    }
                                }
                            }
                            System.out.println("save data định kỳ đã xong");
                        } catch (IOException e) {
                            System.err.println("save data err!!");
                        }
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
                }
            }
        });
    }

    public void close() throws IOException {
        running = false;
        server_live.interrupt();
        server.close();
        instance = null;
    }
}
