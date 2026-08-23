package core;

public class Start {
	public static void main(String[] args) {
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				// Lưu dữ liệu người chơi TRƯỚC khi đóng pool, để SIGTERM/docker stop/Ctrl+C không mất
				// tới ~60s tiến trình chơi của toàn bộ người online. Bọc try để lỗi lưu không chặn việc thoát.
				if (SQL.is_connected) {
					try {
						SaveData.process();
						System.out.println("Shutdown: da luu du lieu.");
					} catch (Throwable t) {
						t.printStackTrace();
					}
					SQL.gI().close();
					System.out.println("SERVER STOPPED!");
				}
			}
		}));
		ServerManager.gI().init();
	}
}
